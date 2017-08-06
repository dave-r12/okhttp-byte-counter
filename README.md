# OkHttp Byte Counter

Count the bytes written and read by an OkHttpClient.

## Android

This won't work on Android, I've opened this issue: https://issuetracker.google.com/issues/64418156

## Usage

```java
ByteCounter byteCounter = new ByteCounter();
OkHttpClient client = new OkHttpClient.Builder()
    .socketFactory(byteCounter.socketFactory())
    .build();

// Make requests...

System.out.println(byteCounter.bytesWritten());
System.out.println(byteCounter.bytesRead());
```
