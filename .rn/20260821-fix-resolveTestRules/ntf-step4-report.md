# NTF Step 4 報告 — nablarch-testing-junit5

指示書: `nablarch-document` `f00e9a6:.rn/20260724-ntf-yaml-support/ntf-step4-04-nablarch-testing-junit5.md`

参照ピン

| 対象 | ハッシュ | 位置づけ |
|---|---|---|
| 追加前 | `2ebea7e` | 指示書が指定した起点 |
| 追加後 | `61712b6` | 本ブランチ `worktree-fix-resolveTestRules` の HEAD |

本書の `file:line` はすべて `61712b6`（追加後）での行番号であり、実測で確認済みである。

---

## 1. 完了条件7 — C0/C1 カバレッジ

### 結果

`src/main`（`nablarch-testing-junit5` バンドル、15 クラス）のカバレッジは**追加前・追加後とも全指標 100%** で、
両者の JaCoCo CSV レポートはバイト単位で一致する（`diff` が差分なし）。

| 指標 | 追加前 `2ebea7e` | 追加後 `61712b6` | 差 |
|---|---|---|---|
| C0（命令網羅 INSTRUCTION） | 403/403 = 100.00% | 403/403 = 100.00% | ±0 |
| C1（分岐網羅 BRANCH） | 8/8 = 100.00% | 8/8 = 100.00% | ±0 |
| 行（LINE、参考） | 105/105 = 100.00% | 105/105 = 100.00% | ±0 |
| メソッド（参考） | 53/53 = 100.00% | 53/53 = 100.00% | ±0 |
| 分岐複雑度（参考） | 57/57 = 100.00% | 57/57 = 100.00% | ±0 |

テストメソッド数は 63 件（`2ebea7e`）→ 78 件（`61712b6`）で 15 件増、いずれも全件成功。

### この値の読み方

**カバレッジは 11 件の追加の効果を示さない。** 追加前の時点で `src/main` は既に全指標 100% であり、
上限に張り付いているため増減が出ない。

11 件が押さえたのは、いずれも**同じ行・同じ分岐を通ったうえで結果が異なる**性質である。
例として #1 はリストの並びが決める入れ子順、#3 は複数フィールドが同一インスタンスかどうか、
#7・#9 は「取得できない」「発火しない」という負の経路。C0/C1 はこれらを区別できない。
したがって「カバレッジが変わらなかったこと」は、追加が無意味だったことを意味しない。

### 計測方法（再現手順）

`pom.xml` に JaCoCo の設定は無く、親 POM `com.nablarch:nablarch-parent:6-NEXT-SNAPSHOT` が
Offline Instrumentation を既定の `build` に持つ（同 POM `:406`-`:425`）。
Offline Instrumentation のままだと `test` フェーズ後の `target/classes` が instrument 済みのまま残り
レポート生成に使えないため、`jacoco.skip=true` で instrument を止め、同じ JaCoCo 0.8.8 の
agent jar を javaagent として渡して計測した。

`jacoco.exec` の出力先は、親 POM の `ci` profile が使う `ci.additionalArgLine`（同 POM `:75`）を
上書きして作業ツリーの外（セッションのスクラッチ領域）へ向けた。**作業ツリーには `jacoco.exec` を残していない**
（`find . -name jacoco.exec` が 0 件、`git status --short` が空。実測）。

```
# 追加後（本ワークツリー）／追加前（2ebea7e を別ワークツリーに切り出して同手順）
AGENT=~/.m2/repository/org/jacoco/org.jacoco.agent/0.8.8/org.jacoco.agent-0.8.8-runtime.jar
mvn -o clean test -Djacoco.skip=true \
    -Dci.additionalArgLine="-javaagent:$AGENT=destfile=<作業ツリー外>/after.exec,append=false"
mvn -o org.jacoco:jacoco-maven-plugin:0.8.8:report -Djacoco.dataFile=<作業ツリー外>/after.exec
# → target/site/jacoco/jacoco.csv を集計
```

`git diff 2ebea7e HEAD -- src/main` は空であり（完了条件4、実測）、
両ピンで計測対象のバイトコードは同一である。したがって差が出ないのは想定どおりである。

---

## 2. 11 件の結果表

**11 件すべてテスト追加（または #11 の名前・Javadoc 修正）で対応済み。`@Ignore` を付けたものは 0 件。**

| # | 対応 | テストの `file:line`（`61712b6`） |
|---|---|---|
| 1 | テスト追加 2 件（正 + 順序を入れ替えた負） | `src/test/java/nablarch/test/junit5/extension/event/TestRuleOrderIntegrationTest.java:29`、`:40` |
| 2 | テスト追加 1 件 | `src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionInjectionTest.java:31` |
| 3 | テスト追加 1 件 | `src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionInjectionTest.java:43` |
| 4 | テスト追加 1 件 | `src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionInjectionTest.java:54` |
| 5 | テスト追加 1 件（例外メッセージも検証） | `src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionInjectionTest.java:64` |
| 6 | テスト追加 2 件（`static` の対照 + インスタンスフィールドの本命） | `src/test/java/nablarch/test/junit5/extension/event/RegisterExtensionFieldIntegrationTest.java:50`、`:59` |
| 7 | テスト追加 2 件（スーパクラス設定・間接設定、いずれも負） | `src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionTest.java:254`、`:266` |
| 8 | テスト追加 2 件（`super` を呼ぶ対照 + 呼ばない本命） | `src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionSuperCallTest.java:34`、`:48` |
| 9 | テスト追加 2 件（`resolveTestRules()` 未実装で発火しない + 実装すると発火する） | `src/test/java/nablarch/test/junit5/extension/event/RuleAnnotationIntegrationTest.java:34`、`:44` |
| 10 | テスト追加 1 件 | `src/test/java/nablarch/test/junit5/extension/http/AbstractHttpRequestTestTemplateIntegrationTest.java:48` |
| 11 | 名前・Javadoc 修正 3 箇所（テストは残した） | `src/test/java/nablarch/test/junit5/extension/event/TimeoutRuleIntegrationTest.java:80`（メソッド名）、`:104`（Javadoc）、`:112`（Javadoc） |

追加したテストメソッドは合計 15 件で、テスト総数 63 → 78 件の増分と一致する（実測）。

### 完了条件2（#1 の負のテスト）

`TestRuleOrderIntegrationTest.java:40`
`リストの順序を入れ替えると入れ子順も入れ替わることをテスト()` が該当する。
`:29` の正のテストと合わせて双方向を押さえている。

---

## 3. 表にない不一致

**0 件。** 解説書の記述と実装の食い違いは、表の 11 件以外に見つけていない。
11 件はいずれも解説書どおりの結果になり、`@Ignore` を付けたものは無い。

### 参考 — 指示書側の記載についての気づき 1 件（不一致ではない）

指示書 #11 は `TimeoutRuleIntegrationTest.java` の解説書参照を 3 箇所（`:80`・`:104`・`:112`）と挙げているが、
`2ebea7e` の同ファイルには他に `:27`（クラス Javadoc）・`:45`・`:50`（定数の Javadoc）・`:114`（Javadoc 本文）の
4 箇所にも解説書への参照がある（`git show 2ebea7e:...` で実測）。本ブランチではこれらも併せて外してあり、
`61712b6` では `src/test` 全体に「解説書」の語が残っていない（`grep -rn 解説書 src/test/` が 0 件、実測）。
解説書と実装の不一致ではなく、指示書の箇所列挙が網羅していなかったという事実のみである。

---

## 4. 完了条件の充足状況

| # | 完了条件 | 結果 | 根拠 |
|---|---|---|---|
| 1 | 11 件すべてに対応がある | OK | §2 の表 |
| 2 | #1 の負のテストがある | OK | `TestRuleOrderIntegrationTest.java:40` |
| 3 | 崩すと落ちることの確認 | ユーザー側で独立に検証済み（本セッションでは未実施） | `/rn:gm` でテスト内容と 78 件全成功を独立検証済みと指示を受けた |
| 4 | `git diff 2ebea7e HEAD -- src/main` が空 | OK | 実測で空 |
| 5 | `ja/` 配下を変更していない | OK | 本リポジトリに `ja/` は無い。解説書の修正案は `document-patch.md` に留めている |
| 6 | `mvn test` が通る | OK | 78 件全件成功（`@Ignore` 0 件） |
| 7 | C0/C1 を計測し追加前後の値を報告に書く。`jacoco.exec` を残さない | OK | §1 |
| 8 | 一時ファイルを残さない | OK | `git status --short` が空 |
| 9 | 変更を push する | OK | 本報告のコミットで実施 |
