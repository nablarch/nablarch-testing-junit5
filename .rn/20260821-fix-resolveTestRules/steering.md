Rn version: 0.8.0
Design: .rn/20260821-fix-resolveTestRules/design.md

# Goal

解説書「JUnit 5用拡張機能 § JUnit 4のTestRuleを再現する」に書かれているとおりに実装しても
JUnit 4 の `TestRule` が再現されない、という報告を受けた。原因を特定し、問題があれば改善する。

調査結果と改善案は、着手前にユーザーへ提示して確認を取る。改善の方向性は
NTF としての方針判断を含むため、ユーザー（およびそのチーム）が判断できる材料を揃えたうえで決定する。

# Acceptance criteria

- 解説書の記載と実装の乖離が、実行可能なテストによって示されている
- 乖離の原因が、一次情報（ソース・コミット・JUnit 公式ドキュメント）に基づいて特定されている
- 改善の選択肢が、判断に必要な事実（影響範囲・変更範囲・JUnit の方針との整合・互換性）とともに提示され、
  ユーザーが方針を決定できる状態になっている
- 決定した方針に沿った変更が実装され、追加した再現テストが成功する
- 既存のテストがすべて成功する。特に `TestEventDispatcherExtensionLifecycleMethodTest`
  （NTF の前処理がユーザーの `@BeforeEach` より先に実行されることを検証する）が成功し続けている
- 実装と解説書・Javadoc の記述が一致している。JUnit 5 の構造上再現できない範囲は、
  再現できない旨と理由が明記されている
- 本リポジトリで完結しない作業（別リポジトリの解説書修正、nablarch-testing 本体の変更）が、
  何が残っているかと合わせて明示されている

# Assumptions

- 事実として確認済み
  - `TestEventDispatcherExtension#emulateTestRules` は `NOOP_STATEMENT` を `base` として
    ルールチェーンを構築し、`beforeEach` の中で `evaluate()` している
  - 解説書の `Timeout` の例は動作しない（1 秒のタイムアウトに対し 2 秒スリープするテストが成功する）
  - `ExternalResource` の後処理がテスト本体より前に実行される（再現テストで FAIL を確認）
  - PR #3（148db9a、2022-01-31）以前は `InvocationInterceptor` を使っており TestRule は動作していた。
    ただし NTF の前処理がユーザーの `@BeforeEach` より後に実行されていた（実測で確認）
  - JUnit 5.11.0 上で `interceptTestMethod` の `invocation.proceed()` を `Timeout` で包むと
    `TestTimedOutException` が発生する（プロトタイプで実測。解説書の `Timeout` の例が実際にタイムアウトする）
  - プロトタイプ適用後、`@ParameterizedTest` でもルールが適用され、
    `TestEventDispatcherExtensionLifecycleMethodTest` は成功し続ける。
    既存テストで落ちるのは `TestEventDispatcherExtensionTest` の 1 件のみで、これは仕様変更そのもの
  - `base` を呼ばないルールを渡すと
    `JUnitException: Chain of InvocationInterceptors never called invocation` になる（実測で確認）
  - JUnit は「JUnit 4 の Rule をネイティブにサポートしないし、今後もしない」と明言している
  - 本モジュールは `junit-jupiter-migrationsupport` を使っていない。JUnit 4 本体
    （`junit:junit:4.13.1`、compile スコープ）に直接依存している
- 前提（未確認）
  - `resolveTestRules()` を実際に利用しているプロジェクトが存在するかどうかは不明。
    存在する場合、ルールの実行位置が変わることによる影響を受ける

# Rules

- commit and push every change; one completion marker per task
- 事実は一次情報で確認する。ソース・コミット・公式ドキュメントのいずれかを出典として示せないものは書かない
- 解説書本体（nablarch/nablarch-document）は別リポジトリのため、本セッションでは修正差分案の作成までとする
- nablarch-testing 本体の変更は本セッションの範囲外とする

# Tasks

### #1: 現状を FAIL するテストで固定する

**Purpose**: 解説書どおりに実装した TestRule がテスト本体を包んでいないことを、実行可能なテストで示す。

**Prerequisites**: none

**Steps**:

- [x] `TestRuleEmulationIntegrationTest` を追加し、ルールの前処理・テスト本体・後処理の実行順を検証する
- [x] 現行実装で FAIL することを確認する
- [x] コミット・プッシュする
- [x] self-check (OK/NG per completion criterion, record in checks/1.md)
- [x] QA expert review (subagent)
- [x] Craft expert review (subagent, per the task's medium)
- [x] Verification expert review (subagent, per the task's medium)

**Completion criteria**:

- 解説書どおりに登録した `TestRule` がテストメソッドの実行を包んでいないことが、テストの失敗として観測できる
- テストの失敗メッセージから、実際の実行順（後処理がテスト本体より前）が読み取れる
- テストがフレームワーク内部の実装詳細ではなく、利用者から見た振る舞いを検証している
  （`resolveTestRules()` をオーバーライドした Extension を実際の JUnit 5 実行に載せている）

### #2: 調査結果と方針案を design.md にまとめる

**Purpose**: 原因・経緯・JUnit 側の前提・選択肢を、チームが方針を判断できる形に整理する。

**Prerequisites**: #1

**Steps**:

- [x] 原因（JUnit 4 と JUnit 5 の拡張ポイントの構造差）を整理する
- [x] 経緯（解説書の追加時期と PR #3 の関係、4 年半検知されなかった理由）を整理する
- [x] JUnit の公式方針とバージョン保守状況を一次情報で確認する
- [x] 選択肢を洗い出し、判断軸ごとに比較する
- [x] コミット・プッシュする
- [x] self-check (OK/NG per completion criterion, record in checks/2.md)
- [x] QA expert review (subagent)
- [x] Craft expert review (subagent, per the task's medium)
- [x] Verification expert review (subagent, per the task's medium)
- [x] Design expert review (subagent)

**Completion criteria**:

- 記載された事実のすべてに、読み手が同じ場所を開いて確認できる出典が添えられている
- 確認できていないことが「未確認」として明示され、確認済みの事実と混ざっていない
- 選択肢が、採らなかった場合に何が残るかまで含めて比較されており、推奨とその理由が示されている
- 第三者が読んで、追加の質問なしに方針を選べる

### #3: Design sign-off

**Purpose**: `resolveTestRules()`（TestRule 再現機構）を存続させるか撤退するかを、ユーザーが決定する。
直し方（design.md §5.2）は選択肢が存在しないため判断対象ではない。

**Prerequisites**: #2

**Steps**:

- [x] design.md をユーザーに提示する
- [x] `/rn:gm` を受けて判断ポイントを 2 段階に整理し直し、直し方を検証済みの差分として提示する
- [x] `/rn:ty`（承認）または `/rn:gm`（修正 → 反映して再提示）で判定を受ける → **1-A（存続させて直す）に決定**

**Completion criteria**:

- 判断1（存続 1-A / 撤退 1-B）が決定されている
- 以降のタスクが、どちらを前提にしているかが確定している

### #4: TestRule の適用先を分離する

**Purpose**: 利用者が `resolveTestRules()` で追加した TestRule がテストメソッドの実行を包むようにする。
NTF 自身が必要とする `TestName` / `TestDescription` の実行位置は変えない。

**Prerequisites**: #3（**1-A 存続 に決定済み**。本タスクはそのまま実施する）

実装差分は design.md §4.1 にある。ただし要約 diff であり、既存 Javadoc の書き換え（#5）は含まない。
プロトタイプで検証したが再現物は残っていない（§4.2）。

**Steps**:

- [x] design.md §4.1 の差分を `src/main` に適用する。**要約 diff なので、同節が挙げる 4 つの省略
      （既存 Javadoc の書き換え・新設メソッドの Javadoc・`interceptTestTemplateMethod` の本体・
      `convert()` の修正）は自分で補う**
- [x] design.md §4.4 (4) の `convert()` の修正を入れる
      （`Description` にテストメソッドのアノテーションを載せる。受け入れずに塞ぐと決めた唯一の制約）
- [x] `TestEventDispatcherExtensionTest` の「TestRuleエミュレート時に例外が発生した場合〜」を
      `interceptTestMethod` 対象に書き換える（design.md §4.3）
- [x] **design.md §4.6 が挙げるテストを追加する。** 件数と個別の列挙は design.md 側を正とし、
      ここでは重複させない（レビューを経て件数は増えている）
- [x] `Timeout` と `DbAccessTestExtension` が併用できないことを実測で確認し、design.md §4.4 (5) の
      記述と一致することを確かめる（`RestTestExtension` はこの問題を持たない）。
      **恒久テストとして残す**（当初は除外する判断だったが、既存の `MockConnectionFactory` /
      `MockTransactionFactory` で実測できることが分かったため反転した。design.md §4.6）
- [x] `@TestFactory` / `DynamicTest`（§4.4 (6)）と `@Nested`（§4.4 (7)）を対象外とする判断を確定させる。
      §4.4 (7) は 1-A 以前からある別課題であり、非互換の一覧には数えない
- [x] `TestRuleEmulationIntegrationTest` のクラス Javadoc から
      「意図的に失敗する」段落を削除する（#1 の Craft レビューからの申し送り）
- [x] self-check (OK/NG per completion criterion, record in checks/4.md)
- [x] QA expert review (subagent)
- [x] Craft expert review (subagent, per the task's medium)
- [x] Verification expert review (subagent, per the task's medium)
- [x] Design expert review (subagent)

**Completion criteria**:

- `TestRuleEmulationIntegrationTest` が成功する
- 解説書の `Timeout` の例と同じ実装で、タイムアウトが実際に発生する
- `TestEventDispatcherExtensionLifecycleMethodTest` が成功し続けている
  （NTF の前処理とテスト名の設定が、ユーザーの `@BeforeEach` より前に行われている）
- `RestTestExtension#beforeEach` の `setUpDb()` が `testDescription` を参照できている
- `@ParameterizedTest` でも TestRule が同様に適用される（#1 で FAIL として固定済み）
- 複数のルールを登録したときの入れ子順が保たれている（#1 で FAIL として固定済み）
- ルールが投げた例外が `RuntimeException` に包まれずそのまま伝播する
- `Description` にテストメソッドのアノテーションが載っている（design.md §4.4 (4)）
- **design.md §4.4 の一覧表のうち「動く」とされた 9 種のルールが、実際に表のとおりに動く**
- `Timeout` と `DbAccessTestExtension` の併用不可が、実測と design.md §4.4 (5) の記述で一致している
- design.md §4.6 のテストがすべて追加され、成功している
- `mvn test` が全件成功する

### #5: Javadoc を実装と一致させる

**Purpose**: `resolveTestRules()` の Javadoc を、実際の適用範囲と制約に合わせる。

**Prerequisites**: #4

**Steps**:

- [x] **`@author` の表記を揃える。** 本セッションで追加したファイルが `Ito Kiyohito`（実装エキスパートが
      git の author から推測）と `Claude`（#1 で追加）で不揃い。ユーザーの回答を得て統一する
- [x] `resolveTestRules()` の Javadoc に **design.md §4.4 の制約 8 点をすべて**明記する
      （解説書には (1)(2)(3)(5)(6) だけを書く。design.md §4.4 冒頭の書き分けに従う）
- [x] design.md §4.4 の「どのルールが使えて何が使えないか」の一覧を Javadoc に反映する
- [x] 基底実装が空リストを返すようになったことを明記する（design.md §4.5 (1)）
- [x] `resolveInternalTestRules()` の Javadoc に、**NTF 内部専用であり利用者は override しないこと**と、
      ここに置いたルールが投げた例外は `RuntimeException` に包まれること（design.md §4.5 (4)）を明記する
- [x] `resolveTestRules()` が `null` を返した場合・リストに `null` が混ざった場合は素の NPE になる。
      ガードするか「`null` を返さないこと」を Javadoc に明記するかを判断する（#4 の Verification レビュー）
- [x] `@ParameterizedTest` では全 invocation の `Description` が同一になり、
      invocation ごとに状態を持つルールは区別できないことを明記する（#4 の Design レビュー）
- [x] `interceptTestMethod` / `interceptTestTemplateMethod` を `final` にした理由を Javadoc に明記する
      （利用者が自分で override すると `resolveTestRules()` のルールが静かに消えるため）。
      **代替手段（別の Extension クラスとして `InvocationInterceptor` を実装する）には但し書きが要る** ——
      別 Extension からは基底の `protected support` フィールドに届かない（#4 の Design レビューが実測）
- [x] `implements InvocationInterceptor` により、`final` にした 2 本以外の default メソッドが
      `@Published` クラスの override 可能面として開くことを明記する
      （`interceptDynamicTest` の 2 オーバーロードを別に数えて 8 本、名前では 7 種。design.md §4.5 (5)）
- [x] `resolveTestRules()` の既存 Javadoc にある「親クラスが返したリストをベースにすること」という
      コード例を、基底実装が空リストを返すようになったことに合わせて書き換える（design.md §4.1 の省略 (a)）
- [x] JUnit 5 に同等機能がある場合はそちらを優先する旨を追記する
- [x] self-check (OK/NG per completion criterion, record in checks/5.md)
- [x] QA expert review (subagent)
- [x] Craft expert review (subagent, per the task's medium)
- [x] Verification expert review (subagent, per the task's medium)

**Completion criteria**:

- Javadoc の記述と実装の振る舞いが一致している
- design.md §4.4 の制約 8 点と「どのルールが使えて何が使えないか」が Javadoc から読み取れる
- 再現できない範囲が、再現できないという事実と理由の両方とともに書かれている
- `resolveInternalTestRules()` が NTF 内部専用であることが Javadoc から明確に読み取れる
- `@author` の表記がリポジトリ内で揃っている
- `javadoc` の生成で、本セッションの変更に起因する新規の警告が出ない
- `mvn test` が全件成功する（Javadoc の変更で振る舞いを変えていないこと）

### #6: 解説書の修正差分案を作成する

**Purpose**: 別リポジトリ（nablarch/nablarch-document）の該当節を、実装と一致させるための差分案を用意する。

**Prerequisites**: #4

**Steps**:

- [x] design.md §6 が挙げる 4 か所（既存 3 か所の修正 + 新規 1 か所の追加）の修正差分案を作成する
- [x] `en/` の対応箇所の修正差分案を作成する
- [x] 本リポジトリでは反映できないこと、および反映先を明記する
- [x] self-check (OK/NG per completion criterion, record in checks/6.md)
- [x] QA expert review (subagent)
- [x] Craft expert review (subagent, per the task's medium)
- [x] Verification expert review (subagent, per the task's medium)

**Completion criteria**:

- 差分案が、そのまま別リポジトリへ適用できる粒度で書かれている
- 修正後の記述どおりに実装すれば動作することが、#4 の成果物で裏づけられている
- ja / en の内容が一致している

### #7: Evaluation sign-off

**Purpose**: Acceptance criteria の充足をユーザーが確認する。

**Prerequisites**: #6

**Steps**:

- [x] Acceptance criteria の実行結果をユーザーに提示する（7 件すべて充足。`mvn -o clean test` = 78 件全件成功）
- [x] `/rn:gm` を受けて NTF Step 4（外部依頼）の完了条件7（C0/C1 カバレッジ）と §5 報告を
      `.rn/20260821-fix-resolveTestRules/ntf-step4-report.md` にまとめる
- [x] `/rn:ty`（承認）または `/rn:gm`（修正 → 反映して再提示）で判定を受ける → **承認**

**Completion criteria**:

- Acceptance criteria の実行結果が承認されている

# State

(written by /rn:dn, read and reset to this placeholder by /rn:up. `Status` is `paused` while a
session is suspended — the signal /rn:up and /rn:dn search for — and resets to `not suspended` here,
so only a genuinely suspended session reads `paused`.)

- **Status**: not suspended（#1–#7 完了、セッションはクローズ済み。中断ではない）
- **Date**: 2026-08-27
- **Last completed**: #7（Evaluation sign-off 承認、`fd1bf31`）。全 7 タスク完了
- **Next**: 本セッションのタスクは残っていない。残るのは本リポジトリ外の作業のみ（下記 Notes）
- **Notes**: ブランチ `worktree-fix-resolveTestRules` / ドラフト PR
  https://github.com/nablarch/nablarch-testing-junit5/pull/12 （push 済み・ツリークリーン・origin と同期）。
  未決は 4 点 —— (1) PR #12 のレビューとマージ（ユーザー側）、(2) 解説書（別リポジトリ
  `nablarch/nablarch-document`）への差分適用＝`document-patch.md`。反映は本モジュールのリリース公開後、
  `pom.xml` が `6-NEXT-SNAPSHOT` のためバージョン未定、(3) 対象外と決めた事項＝`follow-up.md`、
  (4) 公開済み Artifact https://claude.ai/code/artifact/746ed304-8f8e-4629-963d-b75f5c96b7bb は
  Artifact ツールに削除の口がないため claude.ai/code/artifacts のギャラリーからユーザー側で削除。
  NTF Step 4 の外部依頼は本ブランチ上で完了（`ntf-step4-report.md`）。
