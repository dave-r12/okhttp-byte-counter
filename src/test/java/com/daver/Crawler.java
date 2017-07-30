package com.daver;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Cache;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.NamedRunnable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Crawls Google properties and counts all bytes transmitted.
 */
public class Crawler {
  private static final BigDecimal BYTES_IN_MEGABYTE = new BigDecimal(1048576);

  private final OkHttpClient client;
  private final Set<HttpUrl> fetchedUrls = Collections.synchronizedSet(
      new LinkedHashSet<HttpUrl>());
  private final LinkedBlockingQueue<HttpUrl> queue = new LinkedBlockingQueue<>();
  private final ConcurrentHashMap<String, AtomicInteger> hostnames = new ConcurrentHashMap<>();

  public Crawler(OkHttpClient client) {
    this.client = client;
  }

  private void parallelDrainQueue(int threadCount) {
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    for (int i = 0; i < threadCount; i++) {
      executor.execute(new NamedRunnable("Crawler %s", i) {
        @Override protected void execute() {
          try {
            drainQueue();
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      });
    }
    executor.shutdown();
  }

  private void drainQueue() throws Exception {
    for (HttpUrl url; (url = queue.take()) != null; ) {
      if (!fetchedUrls.add(url)) {
        continue;
      }

      Thread currentThread = Thread.currentThread();
      String originalName = currentThread.getName();
      currentThread.setName("Crawler " + url.toString());
      try {
        fetch(url);
      } catch (IOException e) {
        System.out.printf("XXX: %s %s%n", url, e);
      } finally {
        currentThread.setName(originalName);
      }
    }
  }

  public void fetch(HttpUrl url) throws IOException {
    // Skip hosts that we've visited many times.
    AtomicInteger hostnameCount = new AtomicInteger();
    AtomicInteger previous = hostnames.putIfAbsent(url.host(), hostnameCount);
    if (previous != null) hostnameCount = previous;
    if (hostnameCount.incrementAndGet() > 100) return;

    Request request = new Request.Builder()
        .url(url)
        .build();
    Response response = client.newCall(request).execute();
    int responseCode = response.code();

    String contentType = response.header("Content-Type");
    if (responseCode != 200 || contentType == null) {
      response.body().close();
      return;
    }

    MediaType mediaType = MediaType.parse(contentType);
    if (mediaType == null || !mediaType.subtype().equalsIgnoreCase("html")) {
      response.body().close();
      return;
    }

    Document document = Jsoup.parse(response.body().string(), url.toString());
    for (Element element : document.select("a[href]")) {
      String href = element.attr("href");
      HttpUrl link = response.request().url().resolve(href);
      if (link == null) continue; // URL is either invalid or its scheme isn't http/https.
      if (!"google.com".equals(link.topPrivateDomain())) continue;

      queue.add(link.newBuilder().fragment(null).build());
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.out.println("Usage: Crawler <cache dir>");
      return;
    }

    int threadCount = 5;
    long cacheByteCount = 1024L * 1024L * 100L;

    Cache cache = new Cache(new File(args[0]), cacheByteCount);
    final ByteCounter counter = new ByteCounter();
    OkHttpClient client = new OkHttpClient.Builder()
        .cache(cache)
        .socketFactory(counter.socketFactory())
        .build();

    Crawler crawler = new Crawler(client);
    crawler.queue.add(HttpUrl.parse("https://www.google.com"));
    crawler.parallelDrainQueue(threadCount);

    Thread thread = new Thread("ByteCounter") {
      @Override public void run() {
        while (true) {
          System.out.printf("Data written: %sMB, Data read: %sMB%n",
              toMegabytes(counter.bytesWritten()), toMegabytes(counter.bytesRead()));
          try {
            Thread.sleep(10_000);
          } catch (InterruptedException ignored) {
          }
        }
      }
    };
    thread.setDaemon(true);
    thread.start();
  }

  static String toMegabytes(long bytes) {
    return new BigDecimal(bytes)
        .setScale(4, RoundingMode.UNNECESSARY)
        .divide(BYTES_IN_MEGABYTE, RoundingMode.HALF_UP)
        .toPlainString();
  }
}
