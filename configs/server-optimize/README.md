# 야생 약탈 서버 최적화 설정 (Purpur 1.21.11 · Ryzen 5 9600X · 28GB)

**적용 전에 서버를 끄고, 서버 폴더 전체를 백업하세요.**

| 파일 | 서버 안 경로 | 적용 방법 |
|---|---|---|
| `bukkit.yml` | `서버폴더/bukkit.yml` | 덮어쓰기 |
| `spigot.yml` | `서버폴더/spigot.yml` | 덮어쓰기 |
| `purpur.yml` | `서버폴더/purpur.yml` | 고친 적 없으면 덮어쓰기, 고친 적 있으면 항목만 옮겨 적기 |
| `config/paper-global.yml` | `서버폴더/config/paper-global.yml` | **덮어쓰기 금지**, 값만 수정 |
| `config/paper-world-defaults.yml` | `서버폴더/config/paper-world-defaults.yml` | **덮어쓰기 금지**, 값만 수정 |
| `server.properties.txt` | `서버폴더/server.properties` | **덮어쓰기 금지**, 값만 수정 |
| `start.bat` / `start.sh` | `서버폴더/` | 그대로 넣기 (jar 이름이 `purpur.jar` 가 아니면 맨 끝 수정) |

- 덮어쓰는 파일에 빠진 항목은 서버가 켜질 때 기본값으로 자동으로 채워집니다.
- paper 설정 파일은 맨 위의 `_version` 값이 필요하므로 덮어쓰지 말고 값만 바꿉니다.
- 월드별 파일(`world/paper-world.yml` 등)은 건드리지 않아도 됩니다. 전부 `paper-world-defaults.yml` 값을 따릅니다.

## 적용 후 확인
1. 서버를 켜고 콘솔에 설정 오류(빨간 글씨)가 없는지 확인
2. `/spark tps`, `/spark profiler` 로 MSPT 확인 (꾸준히 40ms 이하가 목표)
3. Chunky 로 야생 미리 생성: `/chunky world world` → `/chunky radius 8000` → `/chunky start`
