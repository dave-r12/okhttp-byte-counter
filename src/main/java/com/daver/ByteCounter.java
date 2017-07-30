package com.daver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.SocketFactory;

/**
 * ByteCounter counts the bytes written and read for an OkHttp client.
 *
 * <p>You must configure your OkHttpClient to use the ByteCounter by setting the
 * {@link SocketFactory}. Once you begin making requests, ByteCounter will keep a running sum of the
 * total bytes written and read by the OkHttpClient. You can then query for those values.
 *
 * <p>Example:
 * <pre>   {@code
 *
 *  // Construct the ByteCounter and configure OkHttpClient to use it.
 *  ByteCounter byteCounter = new ByteCounter();
 *  OkHttpClient client = new OkHttpClient.Builder()
 *      .socketFactory(byteCounter.socketFactory())
 *      .build();
 *
 *  // Make requests with the client...
 *
 *  // Get bytes written and read by the client at this moment.
 *  System.out.println("Bytes written: " + byteCounter.bytesWritten());
 *  System.out.println("Byte read: " + byteCounter.bytesRead());
 * }</pre>
 */
public final class ByteCounter {
  private final SocketFactory socketFactory = new CountSocketFactory();

  private final AtomicLong bytesWritten = new AtomicLong();
  private final AtomicLong bytesRead = new AtomicLong();

  /**
   * Returns a {@link SocketFactory} that must be used by an OkHttpClient to count the bytes written
   * and read.
   */
  public SocketFactory socketFactory() {
    return socketFactory;
  }

  /** Returns the total number of bytes written by an OkHttpClient. */
  public long bytesWritten() {
    return bytesWritten.get();
  }

  /** Returns the total number of bytes read by an OkHttpClient. */
  public long bytesRead() {
    return bytesRead.get();
  }

  void bytesRead(int length) {
    while (true) {
      long old = bytesRead.get();
      long updated = old + length;
      if (bytesRead.compareAndSet(old, updated)) break;
    }
  }

  void bytesWritten(int length) {
    while (true) {
      long old = bytesWritten.get();
      long updated = old + length;
      if (bytesWritten.compareAndSet(old, updated)) break;
    }
  }

  final class CountSocketFactory extends SocketFactory {
    @Override public Socket createSocket() throws IOException {
      return new CountingSocket();
    }

    @Override public Socket createSocket(String s, int i) throws IOException {
      return new CountingSocket(s, i);
    }

    @Override public Socket createSocket(String s, int i, InetAddress inetAddress, int i1)
        throws IOException {
      return new CountingSocket(s, i, inetAddress, i1);
    }

    @Override public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
      return new CountingSocket(inetAddress, i);
    }

    @Override public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1,
        int i1) throws IOException {
      return new CountingSocket(inetAddress, i, inetAddress1, i1);
    }
  }

  final class CountingSocket extends Socket {
    private final Object lock = new Object();
    private OutputStream outputStream;
    private InputStream inputStream;

    CountingSocket() {
      super();
    }

    CountingSocket(String host, int port) throws IOException {
      super(host, port);
    }

    CountingSocket(InetAddress address, int port) throws IOException {
      super(address, port);
    }

    CountingSocket(String host, int port, InetAddress localAddr, int localPort)
        throws IOException {
      super(host, port, localAddr, localPort);
    }

    CountingSocket(InetAddress address, int port, InetAddress localAddr, int localPort)
        throws IOException {
      super(address, port, localAddr, localPort);
    }

    @Override public InputStream getInputStream() throws IOException {
      synchronized (lock) {
        if (inputStream == null) {
          inputStream = new CountingInputStream(super.getInputStream(), ByteCounter.this);
        }
      }
      return inputStream;
    }

    @Override public OutputStream getOutputStream() throws IOException {
      synchronized (lock) {
        if (outputStream == null) {
          outputStream = new CountingOutputStream(super.getOutputStream(), ByteCounter.this);
        }
      }
      return outputStream;
    }
  }

  static final class CountingOutputStream extends OutputStream {
    private final OutputStream delegate;
    private final ByteCounter byteCounter;

    CountingOutputStream(OutputStream delegate, ByteCounter byteCounter) {
      this.delegate = delegate;
      this.byteCounter = byteCounter;
    }

    @Override public void write(int b) throws IOException {
      delegate.write(b);
      byteCounter.bytesWritten(1);
    }

    @Override public void write(byte[] b) throws IOException {
      delegate.write(b);
      byteCounter.bytesWritten(b.length);
    }

    @Override public void write(byte[] b, int off, int len) throws IOException {
      delegate.write(b, off, len);
      byteCounter.bytesWritten(len);
    }

    @Override public void flush() throws IOException {
      delegate.flush();
    }

    @Override public void close() throws IOException {
      delegate.close();
    }
  }

  static final class CountingInputStream extends InputStream {
    private final InputStream delegate;
    private final ByteCounter byteCounter;

    CountingInputStream(InputStream delegate, ByteCounter byteCounter) {
      this.delegate = delegate;
      this.byteCounter = byteCounter;
    }

    @Override public int read() throws IOException {
      int read = delegate.read();
      if (read > 0) byteCounter.bytesRead(1);
      return read;
    }

    @Override public int read(byte[] b) throws IOException {
      int read = delegate.read(b);
      if (read > 0) byteCounter.bytesRead(read);
      return read;
    }

    @Override public int read(byte[] b, int off, int len) throws IOException {
      int read = delegate.read(b, off, len);
      if (read > 0) byteCounter.bytesRead(read);
      return read;
    }

    @Override public long skip(long n) throws IOException {
      return delegate.skip(n);
    }

    @Override public int available() throws IOException {
      return delegate.available();
    }

    @Override public void close() throws IOException {
      delegate.close();
    }

    @Override public synchronized void mark(int readlimit) {
      delegate.mark(readlimit);
    }

    @Override public synchronized void reset() throws IOException {
      delegate.reset();
    }

    @Override public boolean markSupported() {
      return delegate.markSupported();
    }
  }
}
