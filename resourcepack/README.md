# Wave 리소스팩 (특별 아이템 텍스쳐)

`shapes.py` 가 특별 아이템 36종의 텍스쳐(32×32)를 도형으로 직접 그려 리소스팩을 만듭니다.
바닐라 텍스쳐를 쓰지 않으므로 다른 파일이 필요 없습니다.

```bash
python3 shapes.py <출력 폴더>   # Pillow 필요
# 출력 폴더 안의 내용을 zip 으로 묶어 서버 리소스팩으로 사용
```

- 1.21.11 (pack format 75), `assets/wave/items/*.json` 아이템 정의 사용
- 플러그인은 `ItemModels` 에 적힌 이름이 들어간 아이템에 `item_model = wave:<키>` 를 붙입니다
- 새 특별 아이템을 추가하면 `shapes.py` 의 ITEMS 와 `ItemModels.java` 양쪽에 같은 키를 넣으세요
