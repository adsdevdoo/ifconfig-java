# ifconfig-java

[![Build](https://github.com/adsdevdoo/ifconfig-java/actions/workflows/build.yml/badge.svg)](https://github.com/adsdevdoo/ifconfig-java/actions/workflows/build.yml)

Java client for the [ifconfig.rs](https://ifconfig.rs) IP / geo lookup service.
Thin wrapper over the JDK `HttpClient` — no extra HTTP dependency, just Jackson
for JSON.

Requires **JDK 21+**.

Changelog: see [CHANGELOG.md](CHANGELOG.md).
Release process: see [RELEASING.md](RELEASING.md).

## Maven

```xml
<dependency>
    <groupId>rs.adsdev</groupId>
    <artifactId>ifconfig-java</artifactId>
    <version>1.1.0</version>
</dependency>
```

## Usage

```java
IfconfigClient client = IfconfigClient.builder()
        .baseUrl("https://ifconfig.rs")   // optional, this is the default
        .apiKey("YOUR_KEY")               // required for /batch only
        .build();

// Own IP + geo
IpInfo me = client.myIp();
System.out.println(me.country() + " / " + me.city());

// Arbitrary IP
IpInfo ru = client.lookup("77.88.55.77");

// Restrict to a few fields (skip serializing the rest)
IpInfo justCountry = client.lookup("8.8.8.8",
        EnumSet.of(Field.COUNTRY, Field.COUNTRY_CODE));

// Same, but via the numeric bitmask encoding (?fields=N)
IpInfo alsoCountry = client.lookup("8.8.8.8",
        Field.toBitmask(EnumSet.of(Field.COUNTRY, Field.COUNTRY_CODE)));

// Plain text (own IP only)
String ip = client.plain();

// Batch lookup (up to 100 items, requires API key)
List<IpInfo> hits = client.batch(List.of(
        new BatchQuery("1.1.1.1"),
        new BatchQuery("8.8.8.8", "country,city")));

// Server-side field bit registry
Map<String, Integer> bits = client.fieldBits();
```

`IfconfigClient` is thread-safe — build one per process and reuse it.

## Error handling

Non-2xx responses and transport failures both surface as
`IfconfigException`. For HTTP errors, `statusCode()` and `body()` carry the
server's response so callers can branch on rate-limit (429), bad request
(400), etc.

## License

Apache 2.0 — see [LICENSE](LICENSE).
