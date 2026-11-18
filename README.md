# Wave Server Plugins

Wave 마인크래프트 서버(Purpur 1.21.x)용 플러그인 3종입니다. Paper API 1.21.1 / Java 21.

| 폴더 | 설명 |
|---|---|
| [`WaveCore`](WaveCore) | 서버 핵심 플러그인 (Skript 변환): 메뉴, 상점, 팀, 보스(천공의 심판자), 상자, 컬렉션, 무기, 튜토리얼 등 |
| [`PremiumPass`](PremiumPass) | 배틀 패스: 무료/프리미엄 트랙, 퀘스트, 교환 상점 |
| [`DailyShop`](DailyShop) | 매일 바뀌는 일일 상점 (패스 포인트 / WaveCore 캐시 결제) |
| [`configs`](configs) | 상자(crates) 설정, PremiumPass 보상/상점 설정 |
| [`maps`](maps) | 천공의 제단 schematic |

## 빌드

각 폴더에서:

```bash
mvn package
```

`target/*-1.0.0.jar` 를 서버 `plugins/` 에 넣습니다. 자세한 설치 순서와 설정은 각 폴더의 README 를 보세요.

## 공개하지 않는 값 (`private.yml`)

IP 잠금, 관리자 닉네임, 문의처, 서버 홍보 링크 같은 실제 서버 값은 저장소에 올리지 않습니다 (`.gitignore`).

1. `WaveCore/private.example.yml` 을 복사해 실제 값을 채웁니다.
2. 둘 중 한 곳에 `private.yml` 로 둡니다.
   - `WaveCore/src/main/resources/private.yml` : 빌드한 jar 에 포함되고, 서버 첫 실행 때 `plugins/WaveCore/private.yml` 로 꺼내집니다.
   - 서버의 `plugins/WaveCore/private.yml` : 바로 적용됩니다.
3. `private.yml` 의 값이 `config.yml` 보다 우선합니다. `/wave reload` 로 다시 불러옵니다.
