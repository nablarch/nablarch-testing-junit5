# 解説書の修正差分案 — 「JUnit 4のTestRuleを再現する」節

タスク #6 の成果物。design.md §6 が挙げた 4 か所（既存 3 か所の修正 + 新規 1 か所の追加）を、
そのまま別リポジトリへ適用できる unified diff に起こしたものである。

**主成果物は §2 の unified diff である。** §3（ja）と §4（en）は、diff の各変更が何を根拠にしているかを
説明する資料であり、適用作業には使わない。§1.4 の手順どおりに §2 を当てれば、手で切り貼りする箇所はない。

## 1. 対象と前提

### 1.1 反映先

**本リポジトリ（`nablarch/nablarch-testing-junit5`）では反映できない。** 解説書は別リポジトリにある。

| 項目 | 値 |
|---|---|
| リポジトリ | `nablarch/nablarch-document` |
| 基準コミット | `5391d5cf721aab89ddd1e570bddec50ec9eefeaa`（`origin/main`、2026-08-05） |
| 対象ファイル（ja） | `ja/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst` |
| 対象ファイル（en） | `en/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst` |
| 対象節 | 「JUnit 4のTestRuleを再現する」 / 「Reproducing the TestRule of JUnit 4」（ja / en とも rst:370-421） |
| 想定する PR の単位 | **ja / en の 2 ファイルを 1 本の PR にまとめる。** 4 か所の修正はすべて同一の実装変更（本リポジトリの `resolveTestRules()` の適用位置の変更）に由来し、片方だけ入れると解説書が実装と食い違う状態が残るため、分割しない |

**本差分案が前提とする実装は、本リポジトリの `1f9b46e`（タスク #5 完了時点）である。**

**この実装を含むリリースのバージョンは未定である。** 本リポジトリの `pom.xml:9` は `6-NEXT-SNAPSHOT` であり、
番号は確定していない（`git tag` にあるのは `1.0.0` / `2.0.0` / `2.1.0` のみで、いずれも本実装より前）。
**解説書の PR がゲートすべきは、前提実装のコミットではなく、この実装が入るリリースの公開である。**
PR を出す前に番号を確認し、そのリリースが公開されるまでマージを待つこと。実装より先に解説書だけを
反映すると、記述が実装と食い違う。

### 1.2 行番号について

**本文書に書いた行番号は、すべて `5391d5c` 時点のものである。** ja / en の双方について
`git show 5391d5c:<path>` で実物を開き、design.md §6 が挙げた行番号を突き合わせて検証した。

- **ja は design.md §6 の行番号と一致していた**（rst:377-391 / rst:395-414 / rst:416-418 / rst:420-421）。ずれはない
- **en の行番号は design.md では未確認だったが、突き合わせた結果 ja と完全に一致していた**（節の開始 rst:370、
  コード例 rst:377-391 と rst:395-414、説明 rst:416-418、注記 rst:420-421、次節の見出し rst:424-426）。
  ja と en は段落単位で 1 対 1 に対応しており、節全体の行数も同じである

**ただし、適用に行番号は使わない。** §2 の unified diff が前後 3 行の文脈で位置を決めるため、
上流で行番号がずれていても、文脈が変わっていなければそのまま当たる。文脈が変わっていれば `git apply` が失敗する。

### 1.3 rst の書式（実物を開いて確認した事項）

**この 2 ファイルは CRLF 改行である。** `file -b` で確認した。また、**コード例の中の「空行」には
半角スペース 2 個が入っている**（`cat -A` で `  ^M$`）。§1.4 の手順は、どちらも保つように組んである。

書式の慣行は同じファイルの他の箇所に合わせた。

- `.. code-block:: java` の本文は**半角スペース 2 個**のインデント（rst:377-391、rst:395-414）
- `.. warning::` は**ディレクティブ行の次に空行を 1 行置き**、本文を半角スペース 2 個でインデントする（rst:94-96。ファイル内で唯一の `warning`）
- **`.. tip::` は空行を置かず**、次の行からそのまま本文を書く（ja の rst:18 / 61 / 175 / 205 / 214 / 430、en の rst:18 / 61 / 174 / 204 / 214 / 430 の 6 か所すべてがこの形。`warning` と流儀が違うので、混ぜないこと）
- 箇条書きは `*` を使う（rst:348-351）
- 強調は `**...**` とし、開始側の `**` の前と、終端側の `**` の後に続く語がある場合は半角スペースを入れる（rst:58）
- クラスへの参照は `:java:extdoc:` ロールを使う（rst:117-119 に `DbAccessTestExtension` / `DbAccessTest` の実例がある）

**インラインマークアップと全角括弧の間の空白は、詰めてよい側と詰めてはいけない側がある。**
以下を docutils に食わせて確認した（`:java:extdoc:` はダミーのロールとして登録）。**反映先が `requirements.txt` で固定している 0.15.2 と、手元の 0.22.4 の両方で結果は同じだった。**

| 書き方 | 結果 |
|---|---|
| ``` ``TestName`` （ など） ``` | 解析できるが、出力に余分な空白が残る |
| ``` ``TestName``（ など） ``` | **WARNING「Inline literal start-string without end-string」** |
| `` :java:extdoc:`X <a.X>` （ :java:extdoc:`Y <a.Y>` ） `` | 解析できるが、出力に余分な空白が残る |
| `` :java:extdoc:`X <a.X>`（:java:extdoc:`Y <a.Y>`） `` | **エラーを出さないまま、`（` 以降が生テキストとして literal に飲み込まれる** |
| ``` （``TestName`` など） ``` | 正しく literal になる |
| `` **強調** 。 `` | 解析できるが、出力に余分な空白が残る |
| `` **強調**。 `` | 正しく strong になる |

つまり、**インラインマークアップの終端の直後に `（` を置くときだけは、間の半角スペースが必須である**
（全角 `（` は docutils が「マークアップ終端の直後に置ける文字」として扱わないため）。
`（` の直後と `）` の直前は詰めるのが慣行で、既存の rst:91 も
``（ここでは :java:extdoc:`TestSupport <nablarch.test.TestSupport>`）`` と閉じ側を詰めている。

**一方、インラインマークアップの終端と句点の間には半角スペースを入れる。** `5391d5c` を `git grep` で数えた結果。

| 書き方 | ja 配下の件数 |
|---|---|
| 二重バッククォートの終端 + 半角スペース + `。` | **126 件** |
| 二重バッククォートの終端 + `。`（詰めた形） | 15 件 |
| `**` の終端 + 半角スペース + `。` | **2 件** |
| `**` の終端 + `。`（詰めた形） | 0 件 |

**合わせて 128 対 15 で、「空白を入れる」が慣行である。** 対象ファイルにはどちらの形も存在しないため、
corpus の多数派に合わせて **`**...** 。` と空けた。`** 。` は出力に余分な半角スペースが残るが（上表）、
それは 126 件ある `` ` `` 側でも同じであり、この解説書群はそれを許容している。
rst:91 は `` ` `` の直後が `）` であって句点ではないので、句点側の判断の根拠にはならない。docutils はどちらでも通る。
§2 の diff はこの方針で書式を揃えてある。

### 1.4 適用手順（受け取った人が実施すること）

**行番号を頼りに手で切り貼りしないこと。** 以下のとおり `git apply` で当てる。
§2 の diff は CR を 1 文字も含まないので、コピー&ペーストで改行コードが変わっても壊れない。
CRLF は手順 2 と 4 で保つ。

```
cd <nablarch-document のワークツリー>
JA=ja/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst
EN=en/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst

# 1. §2 の diff ブロックの中身を patch.diff として保存する
#    §2 のコードフェンスの内側を、最初の "diff --git" 行から最後の区切り線（ハイフンだけの行）まで
#    1 行も欠かさず全部コピーする。本手順で手作業なのはここだけである
#    （改行は LF でも CRLF でもよい。CR は含まれない）

# 2. 対象 2 ファイルを一時的に LF にする
sed -i 's/\r$//' "$JA" "$EN"

# 3. 当てる（--whitespace=nowarn は保険。2 スペースだけの空行 13 か所はいずれもハンクの外にあり、
#     フラグなしでも警告は出ないことを 5391d5c の複製で確認済み）
git apply --whitespace=nowarn patch.diff

# 4. CRLF に戻す
sed -i 's/$/\r/' "$JA" "$EN"

# 5. 検算（§1.5）
wc -l "$JA" "$EN"          # → どちらも 488
file "$JA" "$EN"           # → どちらも "with CRLF line terminators"
git diff --stat            # → 2 files changed, 78 insertions(+), 12 deletions(-)

# 6. textlint を通す（§1.6）
#    textlint-plugin-rst が Python の rst2ast を呼ぶため、npm と Python の両方の環境が要る
npm ci
pip install -r requirements.txt   # docutils==0.15.2 / docutils-ast-writer==0.1.2 を含む
npx textlint "$JA" "$EN"
```

### 1.5 検算（適用後に確認すること）

| 確認すること | 期待値 |
|---|---|
| 行数 | ja / en とも **455 行 → 488 行**（内訳は下表） |
| 改行コード | ja / en とも **CRLF**（`file` の出力に `with CRLF line terminators`） |
| 変更行数 | `git diff --stat` が **2 files changed, 78 insertions(+), 12 deletions(-)**（1 ファイルあたり +39 −6） |
| rst の構文 | `docutils` の system message が **0 件**（既定の `report_level=2` の場合。原文も 0 件。§1.7 参照） |
| コード例の空行 | `git diff` の文脈行で、半角スペース 2 個だけの行が落ちていない |

行数の内訳（ja / en 共通）。

| 修正 | 変更前 | 変更後 | 増減 |
|---|---|---|---|
| 修正1（コード例） | 10 行 | 9 行 | **−1** |
| 修正2（`.. warning::` の挿入） | — | 10 行（末尾の空行を含む） | **+10** |
| 修正3（制約の追記） | — | 21 行（末尾の空行を含む） | **+21** |
| 修正4（注記の書き換え。説明の直後に 2 行 + 空行、節末に 2 行） | 2 行 | 5 行 | **+3** |
| 合計 | 455 行 | 488 行 | **+33** |

**受け取った側の git が `core.autocrlf=true` だと、手順 4 で戻した CR がコミット時に落ちる。**
反映先の `.gitattributes`（`5391d5c`）は `*.bat` しか固定していないため、設定次第でこうなる。
上表の `git diff --stat` が 78/12 にならず全行差分になるので、この検算で気づける。

### 1.6 textlint

**反映先には textlint のゲートがある。** `5391d5c` に `.textlintrc`（`preset-ja-technical-writing` + `prh`）、
`.textlint/conf/prh.yml`、`package.json`（`textlint` 15.7.1 / `textlint-plugin-rst` 0.1.1）がある。
`textlint-plugin-rst` は Python の `rst2ast`（`requirements.txt` の `docutils-ast-writer==0.1.2`）を呼ぶので、
`npm ci` だけでなく `requirements.txt` の環境も要る。

**適用後の 2 ファイルに実際に `npx textlint` を通した結果は次のとおり。**

| ファイル | 原文（`5391d5c`） | 適用後 |
|---|---|---|
| ja | error 3 件（`prh`「利用 => 使用」。rst:56 / rst:324 / rst:433） | error 3 件（同じ 3 件が rst:56 / rst:324 / rst:466 へ移動しただけ） |
| en | error 0 件 | error 0 件 |

**追加分が増やした指摘は 0 件である。** 残る 3 件は原文にもとからあるもので、本差分案とは無関係である
（`prh.yml:17` の `/(再)?利用(?!者|ケース)/` → `使用` ルール）。**そもそも、追記した rst には「利用」を含む語が
1 つもない**（`git grep` で確認。適用後の ja で「利用」が出るのは rst:56 / rst:324 / rst:466 の 3 行だけで、
いずれも原文由来である）。
追記した日本語は `max-ten`（1 文の読点 3 個以内）と `max-kanji-continuous-len`（連続漢字 8 文字以内）にも触れていない。

### 1.7 rst の構文検証

**反映先が `requirements.txt` で固定している `docutils` 0.15.2 と、手元の 0.22.4 の両方**で原文と適用後の
4 ファイルを解析し、**いずれも system message 0 件**であることを確認した
（`:java:extdoc:` / `:ref:` はダミーのロール、`contents` / `code-block` はダミーのディレクティブとして登録した。
`code-block` を登録しないと Pygments の有無に依存した警告が出て、原文と適用後の比較にならない）。

**「0 件」は既定の `report_level=2`（WARNING 以上）での話である。** `report_level=1`（INFO 以上）にすると、
**原文・適用後とも 2 件**の INFO が出る（rst:1 と rst:46 の未参照 hyperlink target。他ファイルから
`:ref:` で参照されるラベルであり、単体解析では未参照になる）。件数も内容も原文と適用後で変わらないので、
本差分案の結論は変わらない。

**箇条書きの中に置いた `.. tip::`（修正3）は、doctree で 1 点目の `list_item` の子になる**ことも確認した
（0.15.2 / 0.22.4 とも `item1: ['paragraph', 'tip']`、`item2`〜`item4` は `['paragraph']`）。

さらに、`5391d5c` の複製に §1.4 の手順をそのまま実行し、**`git apply` が成功すること・488 行 CRLF になること・
`git diff --stat` が 78/12 になること・適用後の 2 ファイルに `npx textlint` を通して指摘が §1.6 の表のとおりになること**
を確認した。作業は一時ディレクトリで行い、`nablarch-document` には一切書き込んでいない
（使ったのは `git show` / `git archive` のみ）。

## 2. 適用する unified diff（主成果物）

**この diff は CR を含まない。** §1.4 の手順 2・4 が CRLF を保つ。

```diff
diff --git a/en/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst b/en/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst
--- a/en/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst
+++ b/en/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst
@@ -404,11 +404,10 @@ When porting this to this extension, the Extension for your own extension is imp
       // 1. Override the resolveTestRules method
       @Override
       protected List<TestRule> resolveTestRules() {
-          // 2. Generate a list based on the result of resolveTestRules() of the parent class
-          List<TestRule> rules = new ArrayList<>(super.resolveTestRules());
-          // 3. Add the TestRule defined in your own extension class to the list
+          List<TestRule> rules = new ArrayList<>();
+          // 2. Add the TestRule defined in your own extension class to the list
           rules.add(((CustomTestSupport) support).timeout);
-          // 4. Return the generated list
+          // 3. Return the generated list
           return rules;
       }
   }
@@ -417,8 +416,42 @@ In the Extension for your own extension, you can override the method ``resolveTe
 Implement this method to return a list of the ``TestRules`` of JUnit 4 that you want to reproduce.
 This allows you to reproduce  ``TestRule`` of JUnit 4 on JUnit 5 tests.
 
-Note that overriding ``resolveTestRules()`` should always be based on the list returned by the parent class ``resolveTestRules()``.
-If not, the ``TestRule`` registered in the parent class will not be reproduced.
+Note that the base implementation of ``resolveTestRules()`` returns an empty list, so you do not need to base your list on the list returned by the parent class ``resolveTestRules()``.
+The ``TestRule`` used internally by the automated testing framework is applied by a mechanism other than this method.
+
+.. warning::
+
+  ``Timeout`` cannot be used together with :java:extdoc:`DbAccessTestExtension <nablarch.test.junit5.extension.db.DbAccessTestExtension>` (:java:extdoc:`DbAccessTest <nablarch.test.junit5.extension.db.DbAccessTest>`), because ``Timeout`` executes the test method in a separate thread.
+  The database connection and the transaction established by the automated testing framework are held bound to the thread that executed the pre-processing, so they cannot be obtained from the test method, which runs in another thread.
+  If the exception raised when obtaining them is caught, **the test succeeds even though the database has not been accessed**.
+  The ``@Timeout`` of JUnit 5 does not execute the test method in a separate thread by default, so this problem does not occur.
+
+  Even when a timeout occurs, the thread that is executing the test method is only interrupted.
+  If that thread is running a process that does not respond to interruption, the post-processing is executed while the test method is still running.
+
+However, JUnit 5 does not officially support the ``TestRule`` of JUnit 4, so the following restrictions apply to this reproduction.
+
+* The rule wraps only the execution of the test method; ``@BeforeEach`` / ``@AfterEach`` and the pre-processing and post-processing of the automated testing framework are not included.
+  Therefore, whereas in JUnit 4 the pre-processing of the rule was executed before ``@Before`` and its post-processing after ``@After``, in this extension the pre-processing of the rule is executed after ``@BeforeEach`` and its post-processing before ``@AfterEach``.
+  For example, code in ``@AfterEach`` that refers to a resource created by ``ExternalResource`` or ``TemporaryFolder``, and code that observes a failure of ``@AfterEach`` with ``Stopwatch``, both **stop working as expected without raising any exception**.
+
+  .. tip::
+    Calling ``getRoot()`` of ``TemporaryFolder`` from ``@BeforeEach`` raises ``IllegalStateException``, because the temporary folder has not been created yet.
+
+* If ``@BeforeEach`` fails, neither the pre-processing nor the post-processing of the rule is executed at all.
+  This is because the rule can be assembled only when the test method is executed, and JUnit 5 does not reach the execution of the test method if ``@BeforeEach`` fails.
+  In JUnit 4, the post-processing of the rule was executed even if ``@Before`` failed.
+  Be careful if you leave the release of resources to the rule: the resources are leaked only when ``@BeforeEach`` fails.
+* A rule that does not call ``base`` (one that skips the test) and a rule that calls ``base`` twice or more (one that retries or repeats the test) cannot be used.
+  In both cases the test fails with an exception (in the latter case, after the test method has been executed once).
+  Here, ``base`` means the first argument of the ``apply`` method of ``TestRule``, that is, the ``Statement`` that the rule wraps.
+* The rule is not applied to the ``DynamicTest`` generated by ``@TestFactory``.
+  In this case, **the test is executed without raising any exception**.
+
+Because of these restrictions, if JUnit 5 has an equivalent feature, use that feature instead of porting the rule.
+
+Because the rule wraps only the execution of the test method, the position where the rules are applied has changed from JUnit 4.
+If you return a rule that only records information before the test is executed (such as ``TestName``), move the code that refers to the value set by the rule from ``@BeforeEach`` into the test method.
 
 
 -------------------------------
diff --git a/ja/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst b/ja/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst
--- a/ja/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst
+++ b/ja/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst
@@ -404,11 +404,10 @@ JUnit 4のTestRuleを再現する
       // 1. resolveTestRules メソッドをオーバーライドする
       @Override
       protected List<TestRule> resolveTestRules() {
-          // 2. 親クラスの resolveTestRules() の結果をベースにしてリストを生成する
-          List<TestRule> rules = new ArrayList<>(super.resolveTestRules());
-          // 3. 独自拡張クラスで定義しているTestRuleをリストに追加する
+          List<TestRule> rules = new ArrayList<>();
+          // 2. 独自拡張クラスで定義しているTestRuleをリストに追加する
           rules.add(((CustomTestSupport) support).timeout);
-          // 4. 生成したリストを返却する
+          // 3. 生成したリストを返却する
           return rules;
       }
   }
@@ -417,8 +416,42 @@ JUnit 4のTestRuleを再現する
 このメソッドで、再現させたいJUnit 4の ``TestRule`` をリストにして返却するように実装する。
 これにより、JUnit 5のテスト上でもJUnit 4の ``TestRule`` を再現できるようになる。
 
-なお、 ``resolveTestRules()`` をオーバーライドするときは、必ず親クラスの ``resolveTestRules()`` が返すリストをベースにすること。
-そうしない場合、親クラスで登録している ``TestRule`` が再現されなくなる。
+なお、 ``resolveTestRules()`` の基底実装は空のリストを返すため、親クラスの ``resolveTestRules()`` が返すリストをベースにする必要はない。
+自動テストフレームワークが内部で使用する ``TestRule`` は、このメソッドとは別の経路で適用される。
+
+.. warning::
+
+  ``Timeout`` はテストメソッドを別スレッドで実行するため、 :java:extdoc:`DbAccessTestExtension <nablarch.test.junit5.extension.db.DbAccessTestExtension>` （:java:extdoc:`DbAccessTest <nablarch.test.junit5.extension.db.DbAccessTest>`）と併用できない。
+  自動テストフレームワークが確立したデータベース接続とトランザクションは、事前処理を実行したスレッドに紐付けて保持されるため、別スレッドで実行されるテストメソッドからは取得できなくなる。
+  取得に失敗したときの例外を捕捉していると、 **データベースにアクセスできていないままテストが成功してしまう** 。
+  JUnit 5の ``@Timeout`` は既定でテストメソッドを別スレッドで実行しないため、この問題は起きない。
+
+  また、タイムアウトが発生しても、テストメソッドを実行しているスレッドには割り込みが行われるだけである。
+  割り込みに反応しない処理を実行している場合、テストメソッドが動き続けたまま事後処理が実行される。
+
+ただし、JUnit 5はJUnit 4の ``TestRule`` を正式にはサポートしていないため、この再現には以下の制約がある。
+
+* ルールが包むのはテストメソッドの実行だけであり、 ``@BeforeEach`` / ``@AfterEach`` や自動テストフレームワークの事前処理・事後処理は含まれない。
+  そのため、JUnit 4ではルールの事前処理が ``@Before`` より前・事後処理が ``@After`` より後に実行されていたのに対し、本拡張機能ではルールの事前処理が ``@BeforeEach`` の後、事後処理が ``@AfterEach`` の前に実行される。
+  例えば、 ``ExternalResource`` や ``TemporaryFolder`` が作成したリソースを ``@AfterEach`` から参照するコードや、 ``@AfterEach`` の失敗を ``Stopwatch`` で観測するコードは、 **例外にならないまま期待どおりに動かなくなる** 。
+
+  .. tip::
+    ``@BeforeEach`` から ``TemporaryFolder`` の ``getRoot()`` を呼び出した場合は、一時フォルダがまだ作成されていないため ``IllegalStateException`` が発生する。
+
+* ``@BeforeEach`` が失敗した場合、ルールは事前処理も事後処理も一切実行されない。
+  ルールを組み立てられるのはテストメソッドを実行する時点であり、JUnit 5は ``@BeforeEach`` が失敗するとテストメソッドの実行に到達しないためである。
+  JUnit 4では ``@Before`` が失敗してもルールの事後処理は実行されていた。
+  リソースの解放をルールに任せている場合、 ``@BeforeEach`` が失敗したときにだけ解放漏れが起きることになるので注意すること。
+* ``base`` を呼び出さないルール（テストをスキップするもの）と、 ``base`` を2回以上呼び出すルール（テストをリトライ・繰り返すもの）は使用できない。
+  どちらもテストが例外で失敗する（後者は、テストメソッドが1回実行されたうえで失敗する）。
+  ここでいう ``base`` とは、 ``TestRule`` の ``apply`` メソッドの第1引数、すなわちルールが包む対象の ``Statement`` である。
+* ``@TestFactory`` が生成した ``DynamicTest`` には、ルールが適用されない。
+  この場合、 **例外にもならないままテストが実行される** 。
+
+これらの制約があるため、JUnit 5に同等の機能がある場合は、ルールを移植するのではなくJUnit 5の機能を使用すること。
+
+また、ルールが包むのはテストメソッドの実行だけであるため、ルールが適用される位置はJUnit 4から変わっている。
+テストの実行前に情報を控えるだけのルール（``TestName`` など）を返却している場合、ルールが設定した値を ``@BeforeEach`` の中で参照しているコードは、テストメソッドの中に移すこと。
 
 
 -------------------------------
```

## 3. ja 側の変更の理由（根拠。適用には §2 を使う）

### 修正1 — コード例から `super.resolveTestRules()` を外す（rst:404-413）

**理由。** 基底実装が空のリストを返すようになったため（`TestEventDispatcherExtension.java:531-533` の
`return Collections.emptyList();`）、`super.resolveTestRules()` をベースにする意味がなくなった。
コメント 2 を削り、以降の番号を繰り上げる。**10 行 → 9 行（−1 行）。**

**変更前（rst:404-413。10 行）**

```
      // 1. resolveTestRules メソッドをオーバーライドする
      @Override
      protected List<TestRule> resolveTestRules() {
          // 2. 親クラスの resolveTestRules() の結果をベースにしてリストを生成する
          List<TestRule> rules = new ArrayList<>(super.resolveTestRules());
          // 3. 独自拡張クラスで定義しているTestRuleをリストに追加する
          rules.add(((CustomTestSupport) support).timeout);
          // 4. 生成したリストを返却する
          return rules;
      }
```

**変更後は §2 の diff を参照。** ここには再掲しない（二重管理を避けるため。以下の修正も同じ）。

### 修正2 — `Timeout` と `DbAccessTestExtension` の併用に対する警告を新規追加

**理由。** design.md §6 のとおり、**この節が挙げている唯一の例が `Timeout` である**以上、必須である。

**挿入位置は design.md §6 の記述（「rst:395-414 の直後」）から意図的に変更してある。** コード例の直後に挟むと、
読者はコードを読み終えた直後に警告を踏まされ、説明（rst:416-418）に戻されることになる。
説明と、修正4 の「`super` をベースにする必要はない」（コード例を見た読者が最初に確認しに来る 2 行）を
読み終えてから、`Timeout` 固有の落とし穴 →（修正3 の）再現機構全体の制約、という順に読ませる。
**挿入する内容は 10 行（末尾の空行を含む）。内容は §2 の diff を参照。**

**警告は 2 段落に分けた。** 前半 3 文は `DbAccessTestExtension` との併用に固有の話だが、後半 2 文は
`Timeout` 一般の性質である。1 段落に続けて書くと、DB を使わず `Timeout` だけを使う読者が 1 文目で読み飛ばしうる。

**あわせて、前半の末尾に「JUnit 5 の `@Timeout` では起きない」を 1 文足した。**
警告に「では何を使えばよいか」がないと、読んだ読者がその場で動けない。文面は
`TestEventDispatcherExtension.java:176` の Javadoc（`JUnit 5 の {@code @Timeout} は既定でテスト本体を
別スレッドで実行しないため、この問題は起きない。`）にすでにある。

**最後の 2 文は、`FailOnTimeout` の実装を読んだうえで最小再現で実測した結果に合わせてある。**
`FailOnTimeout.evaluate()` は `finally` で `thread.join(1)` しか待たない（`junit-4.13.1-sources` の
`FailOnTimeout.java:135`）が、その前に `createTimeoutException()` が `thread.interrupt()` を呼んでいる（`:176`）。
したがって「テストメソッドの実行は打ち切られない」は無条件には成り立たず、**割り込みに反応するかどうかで結果が変わる。**
最小再現（タイムアウト 300 ミリ秒、本体は 1500 ミリ秒）で両方を実測した結果は次のとおり。

| テストメソッドの本体 | 実行ログ |
|---|---|
| 割り込みを無視するビジーループ | `[body-start, afterEach, body-end interrupted=true]` — `@AfterEach` の後まで走り続ける |
| `Thread.sleep(1500)` | `[body-start, body-INTERRUPTED, afterEach]` — `@AfterEach` より前に `InterruptedException` で打ち切られる |

**この 2 文には恒久テストを追加していない。** 最小再現をリポジトリに残す案もあったが、design.md §4.6 が
「タイムアウト成立とスレッド割り込みのタイミング依存で不安定になりやすい」ことを理由に恒久テストにしないと
決めており、その判断と整合させた。裏づけは `FailOnTimeout.java:135`（`thread.join(1)`）と
`:176`（`thread.interrupt()`）という一次情報であり、読み手が同じ場所を開いて確かめられる。

**「静かに壊れる」を指す 3 か所（この警告・修正3 の 1 項目目・修正3 の 4 項目目）は、書式を揃えてある。**
design.md §4.4 (5) はこの経路を「最も影響が大きい」としているのに、原案ではここだけ平文だった。
3 か所とも `**……** 。` で終え、「ので注意すること」は付けない。`.. warning::` の中で「注意すること」と
書くのは警告と二重であり、他の 2 か所と書式が揃わなくなるためである（en 側も 3 か所とも `**……**.` で揃っている）。
この結果、追記全体で「ので注意すること」は修正3 の 2 項目目の 1 回だけになった。

### 修正3 — 再現に付く制約を追記（修正2 の警告の直後）

**理由。** 現行の rst:416-418 は「これにより、JUnit 5のテスト上でもJUnit 4の `TestRule` を再現できるようになる」で
終わっており、再現に付く制約が一切書かれていない。design.md §4.4 の (1)(2)(3)(6) を追記する。

**rst:416-418 の 3 行は変更しない。** 修正2 の警告の直後に挿入する。
**挿入する内容は 21 行（末尾の空行を含む）。内容は §2 の diff を参照。**

**この節でしたこと。**

| 事項 | 内容 |
|---|---|
| 例に挙げるルールを `TestWatcher` から `Stopwatch` へ | **`javap` で確認した結果、JUnit 4.13.1 の `org.junit.rules.Stopwatch` は `TestRule` を直接実装したクラスであり、`TestWatcher` を継承していない**（`public class org.junit.rules.Stopwatch implements org.junit.rules.TestRule`。`TestWatcher` は `public abstract class ... implements TestRule` で、両者は無関係な兄弟）。実測している恒久テストが使っているのは `Stopwatch` なので、名指しするクラスをそちらに合わせた |
| 「テスト本体」を「テストメソッド」へ | **`git grep` の結果、`5391d5c` の ja 配下に「テスト本体」は 0 件、「テストメソッド」は 70 件**。この解説書群に前例のない語を新しく持ち込まない |
| 「事前処理」の 3 つ目の意味を `@BeforeEach` へ | 追記分の中で「事前処理」が (a) 自動テストフレームワークのフック、(b) ルールの前半部、(c) `@BeforeEach` そのもの、の 3 つの意味で使われていた。(c) は 1 項目目が `@BeforeEach` を明示的に除外していることと衝突するので、`@BeforeEach` に置換した |
| `getRoot()` の `IllegalStateException` を `.. tip::` へ、**箇条書きの 1 項目目の中に入れて** | 1 項目目の一般則（適用位置がずれる）の一事例であり、同じ項目に地の文で置くと 4 文になって他項目より重くなる。落とすには具体的で有用なので `.. tip::` に逃がした。ただしリストの外（4 項目目の直後）に置くと 4 項目目に係ると読めるため、**1 項目目の項目の中（本文と同じ 2 スペースのインデント）に置いた**。docutils 0.15.2 / 0.22.4 の双方で、tip が 1 項目目の `list_item` の子になること（`item1: ['paragraph', 'tip']`、`item2`〜`item4` は `['paragraph']`）を確認済み |
| 「JUnit 5 に同等の機能がある場合はそちらを使うこと」を追加 | 修正後のこの節は「静かに壊れる形」を 4 つ列挙することになる。そこまで読んだ利用者が最初に取れる行動を書かないのは不親切である。文面は `TestEventDispatcherExtension#resolveTestRules()` の Javadoc にすでにある |

### 修正4 — rst:420-421 を書き換え、節の 2 か所に分けて置く

**理由。** 基底実装が空のリストを返すようになったため、「必ず親クラスの `resolveTestRules()` が返すリストを
ベースにすること」という指示と、その理由づけ（「そうしない場合、親クラスで登録している `TestRule` が
再現されなくなる」）がどちらも事実でなくなる。あわせて、ルールの適用位置が移ることに対する移行手順を書く。

**`resolveInternalTestRules()` は移行先として案内しない**（design.md §4.5 (2)。NTF 内部専用であり、
そこに置いたルールは「テストメソッドを包まない」＋「例外が `RuntimeException` に包まれる」という別の意味論を持つ）。
代わりに「別の経路で適用される」とだけ書き、メソッド名は出さない。

**この 2 行の置き換え先は、節の中の 2 か所に分かれる。**

* **「基底実装は空のリストを返すため `super` をベースにする必要はない」の 2 行は、説明（rst:416-418）の直後**、
  つまり修正2 の警告よりも前に置く。コード例から `super.resolveTestRules()` が消えたのを見た読者が
  「`super` は要らないのか」を確認しに来るのが、この節の主動線である。警告 10 行・制約 21 行を挟んだ先に置くと、
  最もよく読まれる 2 行が読者から最も遠くなる
* **移行手順の 2 行は、制約とその帰結であるから制約リストの後ろに残す。** 副次効果として、
  「JUnit 5 に同等の機能がある場合はそちらを」が節末近くに寄り、結論の位置が正しくなる

**「ルールの適用位置が `@BeforeEach` の後である」という事実は、修正3 の 1 項目目に書いてある。**
ここで繰り返さず、移行指示だけを書く。**ただし、参照の仕方は序数をやめて内容で書いた。**
原案の「上述の制約の1点目のとおり」（en: `As described in the first restriction above`）は、
箇条書きを 1 つ足すと黙って壊れる。「ルールが包むのはテストメソッドの実行だけであるため」と、
参照先の内容そのものを書いてある。

**変更前（rst:420-421。2 行）**

```
なお、 ``resolveTestRules()`` をオーバーライドするときは、必ず親クラスの ``resolveTestRules()`` が返すリストをベースにすること。
そうしない場合、親クラスで登録している ``TestRule`` が再現されなくなる。
```

**変更後は合計 5 行**（説明の直後に 2 行 + 空行、節末に 2 行）。**内容は §2 の diff を参照。**

## 4. en 側の変更の理由（根拠。適用には §2 を使う）

**ja の修正1〜修正4 と 1 対 1 に対応する。理由・挿入位置・行数は、いずれも対応する ja の修正と同じである。**

| en の修正 | 理由 |
|---|---|
| 修正1en（コード例。10 行 → 9 行） | ja 修正1 の理由に同じ |
| 修正2en（`.. warning::` の挿入。10 行） | ja 修正2 の理由に同じ |
| 修正3en（制約の追記。21 行） | ja 修正3 の理由に同じ |
| 修正4en（注記の書き換え。2 行 → 5 行） | ja 修正4 の理由に同じ |

文体は ja の直訳ではなく、同じファイルの周辺の英文に合わせた
（`the automated testing framework` / `pre-processing` / `post-processing` / `your own extension class`）。
そのうえで、次の点を直した。

| 事項 | 内容 |
|---|---|
| `Note that` の乱用 | 追記分で 5 回使っていた。**`5391d5c` の en 側ファイル全体では 2 回しか使われていない**（rst:232 と rst:420。どちらも文頭の `Note that <事実>`）。原文 rst:420 を引き継ぐ 1 か所だけ残し、残りは `If ...` / `In this case, ...` に置き換えた |
| `applied through a route other than this method` | 直訳的。`applied by a mechanism other than this method` にした |
| `so it does not need to be based on ...` | `it` の指示対象が不明。`so you do not need to base your list on ...` にした |
| `code that refers from ``@AfterEach`` to a resource ...` | 不自然。`code in ``@AfterEach`` that refers to a resource ...` にした |
| `the body of the test method` | ja の「テスト本体」に対応する語。**`5391d5c` の en 配下に `body of the test` は 0 件**。ja に合わせて `the test method` に統一した |
| `because it executes the test method in a separate thread` | `it` が直前の `DbAccessTest` を指しうる。`because ``Timeout`` executes the test method in a separate thread` にした |
| `code in ..., and code that ..., stop working` | 不可算名詞 `code` の並列に複数動詞が付いていた。`..., both **stop working as expected without raising any exception**.` にした |
| ja の喚起に対応する英文がない | ja は「ので注意すること」を修正3 の 2 項目目で 1 回使うが、en 側は平叙文のままだった。同じファイルの既存ペア（ja rst:96「値は設定しないこと」/ en rst:96 `Don't set any value to the field`）に倣い、`Be careful if you leave the release of resources to the rule: ...` と命令形にした |

## 5. 各記述の裏づけ

**「修正後の記述どおりに実装すれば動作する」ことの裏づけは、原則として本リポジトリ（`1f9b46e`）の
`src/test` にある恒久テストである。** design.md は根拠にしていない。恒久テストがない 1 件は、
一次情報（JUnit 4 のソース）と最小再現の実測を根拠にしていることを明記した。

| 差分案の記述 | 裏づけ |
|---|---|
| **修正1** — `super.resolveTestRules()` をベースにしなくてもルールが適用される | `src/test/java/nablarch/test/junit5/extension/event/ConfigurableTestRuleExtension.java:55-58` が `super` を呼ばずに設定されたリストをそのまま返す実装であり、`StandardTestRuleIntegrationTest`（9 件）・`TestRuleLifecycleIntegrationTest`（2 件）・`TestRuleInvocationContractIntegrationTest`（4 件）・`TestFactoryRuleIntegrationTest`（1 件）がすべてこの Extension 経由でルールの適用を表明している |
| **修正1** — **修正後の**コード例と同じ形（`new ArrayList<>()` にサポートクラスの `@Rule Timeout` を追加して返す）でテストがタイムアウトする | `TimeoutRuleIntegrationTest.java:79-86`（`resolveTestRulesで返したTimeoutが適用されてテストがタイムアウトすることをテスト`）。フィクスチャは `:103-142` にあり、**タスク #6 で修正後のコード例と同じ `new ArrayList<>()` の形に変更した**（`:127`）。基底実装が空のリストを返すため挙動は変わらず、`mvn -o clean test` は 78 件全件成功する |
| **修正2** — `Timeout` を併用するとテストメソッドから DB コネクションもトランザクションも取得できない／それでもテストは成功する | `src/test/java/nablarch/test/junit5/extension/db/TimeoutDbAccessIntegrationTest.java:92-109`（`Timeoutを併用すると…`）。`:79-90` に `DbAccessTestExtension` 単体との対照がある。失敗時の例外は `IllegalArgumentException: specified database connection name is not register in thread local. connection name = [transaction]` |
| **修正2** — テストメソッドが `beforeEach` とは別スレッド（`Time-limited test`）で実行される | `TimeoutRuleIntegrationTest.java:88-101`、`TimeoutDbAccessIntegrationTest.java:97-98` |
| **修正2** — 「タイムアウトが発生してもスレッドには割り込みが行われるだけであり、割り込みに反応しない処理は事後処理の後まで動き続ける」 | **恒久テストはない。**一次情報は `junit-4.13.1-sources` の `org/junit/internal/runners/statements/FailOnTimeout.java:176`（`createTimeoutException()` の中の `thread.interrupt()`）と `:135`（`finally` の `thread.join(1)`）。**両方の分岐を最小再現で実測した**（§3 修正2 の表）。最小再現はタスク #6 の作業中に一時的に作成して実行し、リポジトリには残していない |
| **修正3 第1項** — ルールの事前処理が `@BeforeEach` の後、事後処理が `@AfterEach` の前 | `TestRuleLifecycleIntegrationTest.java:28-37`。実行ログ `["@BeforeEach", "resource-before", "test", "resource-after", "@AfterEach"]` を表明している |
| **修正3 第1項** — `@AfterEach` の失敗を `Stopwatch` で観測できない | `StandardTestRuleIntegrationTest.java:173-183`（`Stopwatchで実行時間を計測できるが_AfterEachの失敗はfailedで観測できないことをテスト`）。使っているルールは `:316` の `RecordingStopwatch extends Stopwatch`。実行ログ `["test", "succeeded", "finished", "@AfterEach"]` |
| **修正3 第1項の `.. tip::`** — `@BeforeEach` から `TemporaryFolder#getRoot()` を呼ぶと `IllegalStateException` | `StandardTestRuleIntegrationTest.java:93-110`。例外型と `the temporary folder has not yet been created` を表明し、実行ログ `["@BeforeEach:root-not-created", "rule-before", "test:root-exists", "rule-after"]` で「適用位置がずれている」と「適用されていない」を区別している |
| **修正3 第2項** — `@BeforeEach` が失敗するとルールの事前処理も事後処理も走らない | `TestRuleLifecycleIntegrationTest.java:39-47`。実行ログ `["@BeforeEach", "@AfterEach"]`（`resource-before` も `resource-after` も現れない） |
| **修正3 第3項** — `base` を呼ばないルールは失敗し、テストメソッドも実行されない | `TestRuleInvocationContractIntegrationTest.java:46-56`。`JUnitException`（`never called invocation`）と実行ログが空であることを表明 |
| **修正3 第3項** — `base` を 2 回呼ぶルールは、テストメソッドが 1 回実行されたうえで失敗する | `TestRuleInvocationContractIntegrationTest.java:58-68`。`JUnitException`（`multiple times`）と実行ログ `["test"]` |
| **修正3 第4項** — `@TestFactory` の動的テストにルールが適用されず、例外にもならない | `TestFactoryRuleIntegrationTest.java:36-45`。テストは成功し、実行ログは `["factory-body", "dynamic-1"]` でルールの前後処理が現れない |
| **修正3 の末尾** — 「JUnit 5 に同等の機能がある場合はそちらを使うこと」 | 恒久テストで裏づける類の記述ではない。文面は `TestEventDispatcherExtension.java` の `resolveTestRules()` の Javadoc にすでにある |
| **修正4** — 基底実装が空のリストを返す | `src/main/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtension.java:531-533`（`return Collections.emptyList();`）。「NTF の内部ルールは別経路」は `:479-481` の `resolveInternalTestRules()` と `:322-330` の `applyInternalTestRules` |
| **修正4** — ルールの適用位置が `@BeforeEach` の後、テストメソッドの直前 | `TestRuleLifecycleIntegrationTest.java:28-37`（上と同じ実行ログ）。`@Test` 以外の経路（`@ParameterizedTest` / `@RepeatedTest`）でも**テストメソッドを包むことは同じ**であることは `TestRuleEmulationIntegrationTest.java:117-131`（3 件）。ただしこのテストクラスには `@BeforeEach` がないため、`@BeforeEach` との前後関係までは表明していない |

**「タイムアウト時の挙動は未実測である」として扱いをレビューに委ねていた項目は、実測したのでなくなった。**
以前の版はこの 1 件を「レビュー依頼時に伝えること」として本文から切り出していたが、
`FailOnTimeout.java:176` の `thread.interrupt()` を見落としたまま `thread.join(1)` だけから導いた帰結だった。
実測して条件付きであることが分かったので、**帰結ではなく実測した事実を修正2 の本文に書き、切り出しは削除した。**
レビュー時に判断を仰ぐ項目は残っていない。

## 6. 本差分案が意図的に含めなかったもの

design.md §6 の末尾にあるとおり、以下は解説書には入れていない。**判断したことを残すために記録する。**

| 入れなかったもの | 理由 |
|---|---|
| 例外の扱いの変更（`resolveTestRules()` で返したルールが投げた例外が `RuntimeException` に包まれなくなる。design.md §4.5 (4)） | 当該節（rst:370-421）にもともと例外の扱いの記述がなく、書かれていない前提が変わっただけであるため。Javadoc（タスク #5）には明記済み |
| `resolveInternalTestRules()` の存在 | NTF 内部専用であり（design.md §4.5 (2)）、利用者に開くと非対称な例外ポリシーを公開 API として抱えることになるため。修正4 では「別の経路で適用される」とだけ書き、メソッド名は出していない |
| `interceptTestMethod` / `interceptTestTemplateMethod` を `final` にしたこと（design.md §4.5 (5)） | 解説書の手順どおりに `resolveTestRules()` をオーバーライドするだけの利用者には影響がなく、影響を受ける利用者はコンパイルエラーで気づくため |
| `@Nested` を持つテストクラスで正しく動かないこと（design.md §4.4 (7)） | `TestRule` 再現機構に固有の問題ではなく、1-A 以前からある別課題であるため。Javadoc には明記済み |
| `@ParameterizedTest` で全 invocation の `Description` が同一内容になること（design.md §4.4 (4) の残り） | 1-A で直ったことでも 1-A で受け入れた制約でもなく、JUnit 5 側の性質であるため。Javadoc には明記済み |
