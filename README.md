# Wave Server Plugins

Wave 마인크래프트 서버(Purpur 1.21.11)용 플러그인 3종입니다. Paper API 1.21.11 / Java 21.

| 폴더 | 설명 |
|---|---|
| [`WaveCore`](WaveCore) | 서버 핵심 플러그인 (Skript 변환): 메뉴, 상점, 팀, 보스(천공의 심판자), 상자, 컬렉션, 무기, 튜토리얼 등 |
| [`PremiumPass`](PremiumPass) | 배틀 패스: 무료/프리미엄 트랙, 퀘스트, 교환 상점 |
| [`DailyShop`](DailyShop) | 매일 바뀌는 일일 상점 (패스 포인트 / WaveCore 캐시 결제) |
| [`Common`](Common) | PremiumPass · DailyShop 이 같이 쓰는 도우미 (각 jar 안에 들어가므로 서버에 따로 넣지 않음) |
| [`TestSupport`](TestSupport) | 테스트에서만 쓰는 도우미 (서버 jar 에 들어가지 않음) |
| [`configs`](configs) | 상자(crates) 설정, PremiumPass 보상/상점 설정 |
| [`maps`](maps) | 천공의 제단 schematic |

## 빌드

저장소 맨 위 폴더에서 한 번에 빌드합니다 (Common → TestSupport → PremiumPass → WaveCore → DailyShop 순서, 테스트 포함).

```bash
mvn install
```

테스트를 건너뛰려면 `mvn install -DskipTests` 입니다.

각 폴더의 `target/*-1.0.0.jar` 를 서버 `plugins/` 에 넣습니다 (`original-*.jar` 는 넣지 않습니다).
자세한 설치 순서와 설정은 각 폴더의 README 를 보세요.

## 테스트

각 플러그인의 `src/test` 에 MockBukkit(가짜 서버)으로 돌리는 테스트가 있습니다. 위의 `mvn install` 에서 함께 실행됩니다.
연동 테스트는 대역(가짜 API) 없이 진짜 플러그인을 함께 올려서 확인합니다 (WaveCore ↔ PremiumPass, DailyShop ↔ WaveCore).

GUI 미리보기용 JSON 이 필요하면 `mvn test -Dsnapshot.dir=폴더` 로 실행합니다. 주지 않으면 아무 파일도 만들지 않습니다.

## 공개하지 않는 값 (`private.yml`)

IP 잠금, 관리자 닉네임, 문의처, 서버 홍보 링크 같은 실제 서버 값은 저장소에 올리지 않습니다 (`.gitignore`).

1. `WaveCore/private.example.yml` 을 복사해 실제 값을 채웁니다.
2. 서버의 `plugins/WaveCore/private.yml` 로 둡니다.
   - ⚠️ `WaveCore/src/main/resources/` 에 넣고 빌드하면 jar 안에 그대로 들어갑니다. jar 를 다른 사람에게 주거나 올리면 IP · 관리자 닉네임이 함께 나가므로 넣지 마세요.
3. `private.yml` 의 값이 `config.yml` 보다 우선합니다. `/wave reload` 로 다시 불러옵니다.

## 라이선스

**All rights reserved.** 이 저장소는 열람용으로만 공개되어 있으며, 저작권자의 허락 없이 복제 · 수정 · 배포 · 다른 서버에서의 사용을 할 수 없습니다. 자세한 내용은 [`LICENSE`](LICENSE) 를 보세요.
