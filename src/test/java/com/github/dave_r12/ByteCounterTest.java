/*
 * Copyright (C) 2017 Dave Roberge.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.dave_r12;

import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ByteCounterTest {
  @Rule public final MockWebServer server = new MockWebServer();

  private final ByteCounter byteCounter = new ByteCounter();
  private OkHttpClient client;

  @Before public void setUp() {
    client = new OkHttpClient.Builder()
        .socketFactory(byteCounter.socketFactory())
        .build();
  }

  @Test public void countTheBytes() throws IOException {
    server.enqueue(new MockResponse()
        .setBody("ABC"));
    server.enqueue(new MockResponse()
        .setBody("ABC"));

    Request request = new Request.Builder()
        .url(server.url("/"))
        .build();
    Response response = client.newCall(request).execute();

    assertEquals("ABC", response.body().string());
    assertEquals(114L, byteCounter.bytesWritten());
    assertEquals(41L, byteCounter.bytesRead());

    Response response2 = client.newCall(request).execute();
    assertEquals("ABC", response2.body().string());
    assertEquals(228L, byteCounter.bytesWritten());
    assertEquals(82L, byteCounter.bytesRead());
  }
}
