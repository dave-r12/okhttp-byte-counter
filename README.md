# OkHttp Byte Counter

Count the bytes written and read by an OkHttpClient.

## Android

The TLS implementation on Android does not allow this to work out of the box. Once this change is merged, it should hopefully work on Android: https://github.com/google/conscrypt/issues/65

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
