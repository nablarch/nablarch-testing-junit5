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
    `TestTimedOutException` が発生する（実測で確認）
  - JUnit は「JUnit 4 の Rule をネイティブにサポートしないし、今後もしない」と明言している
  - 本モジュールは `junit-jupiter-migrationsupport` を使っていない。JUnit 4 本体
    （`junit:junit:4.13.1`、compile スコープ）に直接依存している
- 前提（未確認）
  - `resolveTestRules()` を実際に利用しているプロジェクトが存在するかどうかは不明。
    存在する場合、ルールの実行位置が変わることによる影響を受ける
  - 本モジュールが JUnit 6 上で動作するかは未検証（内部 API `org.junit.platform.commons.util.ReflectionUtils` を使用している）

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
- [ ] self-check (OK/NG per completion criterion, record in checks/1.md)
- [ ] QA expert review (subagent)
- [ ] Craft expert review (subagent, per the task's medium)
- [ ] Verification expert review (subagent, per the task's medium)

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
- [ ] self-check (OK/NG per completion criterion, record in checks/2.md)
- [ ] QA expert review (subagent)
- [ ] Craft expert review (subagent, per the task's medium)
- [ ] Verification expert review (subagent, per the task's medium)
- [ ] Design expert review (subagent)

**Completion criteria**:

- 記載された事実のすべてに、読み手が同じ場所を開いて確認できる出典が添えられている
- 確認できていないことが「未確認」として明示され、確認済みの事実と混ざっていない
- 選択肢が、採らなかった場合に何が残るかまで含めて比較されており、推奨とその理由が示されている
- 第三者が読んで、追加の質問なしに方針を選べる

### #3: Design sign-off

**Purpose**: 改善の方針（案 A / 案 B / 案 C）をユーザーが決定する。

**Prerequisites**: #2

**Steps**:

- [ ] design.md をユーザーに提示する
- [ ] `/rn:ty`（承認）または `/rn:gm`（修正 → 反映して再提示）で判定を受ける

**Completion criteria**:

- design.md が承認されている
- 以降のタスクが、どの方針を前提にしているかが確定している

### #4: TestRule の適用先を分離する

**Purpose**: 利用者が `resolveTestRules()` で追加した TestRule がテストメソッドの実行を包むようにする。
NTF 自身が必要とする `TestName` / `TestDescription` の実行位置は変えない。

**Prerequisites**: #3（案 B が選択された場合。他の案が選択された場合は本タスクを差し替える）

**Steps**:

- [ ] `TestEventDispatcherExtension` に `InvocationInterceptor` を実装し、`resolveTestRules()` が返すルールで
      `invocation.proceed()` を包む
- [ ] `interceptTestTemplateMethod` を実装し、`@ParameterizedTest` / `@RepeatedTest` でも同様に動作させる
- [ ] `TestName` / `TestDescription` を適用する内部経路を `beforeEach` 側に分離する
- [ ] `SimpleRestTestExtension` の `testDescription` の登録先を内部経路へ移す
- [ ] self-check (OK/NG per completion criterion, record in checks/4.md)
- [ ] QA expert review (subagent)
- [ ] Craft expert review (subagent, per the task's medium)
- [ ] Verification expert review (subagent, per the task's medium)
- [ ] Design expert review (subagent)

**Completion criteria**:

- `TestRuleEmulationIntegrationTest` が成功する
- 解説書の `Timeout` の例と同じ実装で、タイムアウトが実際に発生する
- `TestEventDispatcherExtensionLifecycleMethodTest` が成功し続けている
  （NTF の前処理とテスト名の設定が、ユーザーの `@BeforeEach` より前に行われている）
- `RestTestExtension#beforeEach` の `setUpDb()` が `testDescription` を参照できている
- `@ParameterizedTest` でも TestRule が同様に適用される
- `mvn test` が全件成功する

### #5: Javadoc を実装と一致させる

**Purpose**: `resolveTestRules()` の Javadoc を、実際の適用範囲と制約に合わせる。

**Prerequisites**: #4

**Steps**:

- [ ] `resolveTestRules()` の Javadoc に、ルールが包む範囲（テストメソッドのみ）を明記する
- [ ] `@BeforeEach` / `@AfterEach` が包まれないこととその理由を明記する
- [ ] JUnit 5 に同等機能がある場合はそちらを優先する旨を追記する
- [ ] self-check (OK/NG per completion criterion, record in checks/5.md)
- [ ] QA expert review (subagent)
- [ ] Craft expert review (subagent, per the task's medium)
- [ ] Verification expert review (subagent, per the task's medium)

**Completion criteria**:

- Javadoc の記述と実装の振る舞いが一致している
- 再現できない範囲が、再現できないという事実と理由の両方とともに書かれている

### #6: 解説書の修正差分案を作成する

**Purpose**: 別リポジトリ（nablarch/nablarch-document）の該当節を、実装と一致させるための差分案を用意する。

**Prerequisites**: #4

**Steps**:

- [ ] `ja/` の該当 rst の修正差分案を作成する
- [ ] `en/` の該当 rst の修正差分案を作成する
- [ ] 本リポジトリでは反映できないこと、および反映先を明記する
- [ ] self-check (OK/NG per completion criterion, record in checks/6.md)
- [ ] QA expert review (subagent)
- [ ] Craft expert review (subagent, per the task's medium)
- [ ] Verification expert review (subagent, per the task's medium)

**Completion criteria**:

- 差分案が、そのまま別リポジトリへ適用できる粒度で書かれている
- 修正後の記述どおりに実装すれば動作することが、#4 の成果物で裏づけられている
- ja / en の内容が一致している

### #7: Evaluation sign-off

**Purpose**: Acceptance criteria の充足をユーザーが確認する。

**Prerequisites**: #6

**Steps**:

- [ ] Acceptance criteria の実行結果をユーザーに提示する
- [ ] `/rn:ty`（承認）または `/rn:gm`（修正 → 反映して再提示）で判定を受ける

**Completion criteria**:

- Acceptance criteria の実行結果が承認されている

# State

- **Status**: paused
- **Date**: 2026-08-21
- **Last completed**: なし。#1・#2 は成果物の作成とコミットまで完了、self-check と各レビューが未実施
- **Next**: プランゲートの承認（`/rn:ty`）または修正指示（`/rn:gm`）
- **Notes**: ブランチ `worktree-fix-resolveTestRules` / ドラフト PR https://github.com/nablarch/nablarch-testing-junit5/pull/12 。
  改善方針は未確定。`design.md` は案 B（TestRule の適用先を分離する）を採用案として記載しているが、決定は #3 Design sign-off で行う。
  案 A / 案 C が選ばれた場合は #4〜#6 を差し替える。
  未処理: 調査中に公開したアーティファクト https://claude.ai/code/artifact/746ed304-8f8e-4629-963d-b75f5c96b7bb は
  Artifact ツールに削除の口がないため削除できていない。claude.ai/code/artifacts のギャラリーからユーザーが削除する必要がある。
