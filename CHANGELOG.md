# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-07-06

### Added

- `IfconfigClient.lookup(String, int)` overload that selects fields via the
  numeric bitmask encoding (`?fields=N`); build the mask with
  `Field.toBitmask(...)`.
- `IfconfigClient.MAX_BATCH_SIZE` constant (100). `batch()` now rejects an
  empty list and any list over the cap before making a request.
- Input validation: blank `ip` (`lookup`/`xml`) and blank `baseUrl` are
  rejected with `IllegalArgumentException`; `batch()` without an API key
  fails fast with `IllegalStateException`.
- `IfconfigException(String, int, String, Throwable)` constructor that carries
  the HTTP status and raw body alongside a cause.

### Changed

- Requests now send an endpoint-appropriate `Accept` header: `text/plain` for
  `/plain` and `application/xml` for `/xml` (JSON endpoints unchanged).
- Response bodies are decoded using the response `Content-Type` charset
  (falling back to UTF-8) instead of forcing UTF-8.
- `IfconfigException` is now `final` and declares a `serialVersionUID`.
- Removed the no-op `@JsonInclude(NON_NULL)` from `IpInfo` (it is only ever
  deserialized).
- Build: import `jackson-bom` so all Jackson modules stay on one coherent
  version, and mark `jakarta.annotation-api` `optional` so it is not forced
  onto consumers' runtime classpaths.

### Fixed

- A 2xx response whose body cannot be parsed now raises an `IfconfigException`
  carrying the HTTP status and raw body, instead of a bare parse error
  indistinguishable from a transport failure.
- `baseUrl` values with multiple trailing slashes are normalized correctly.

## [1.0.1] - 2026-05-29

### Fixed

- Preserve query-parameter order in generated request URIs.

## [1.0.0] - 2026-05-29

- Initial release: thin JDK `HttpClient` wrapper over the ifconfig.rs
  IP / geo lookup service.

[1.1.0]: https://github.com/adsdevdoo/ifconfig-java/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/adsdevdoo/ifconfig-java/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/adsdevdoo/ifconfig-java/releases/tag/v1.0.0
