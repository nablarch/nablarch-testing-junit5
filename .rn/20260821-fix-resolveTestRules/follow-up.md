# 申し送り事項 — 本 PR の対象外とした事項

本 PR は「解説書どおりに実装しても JUnit 4 の `TestRule` が再現されない」という**不具合の対応**に範囲を絞っている。
調査の過程で見つかったが、その不具合とは独立している事項をここに記録する。

**本ファイルは報告であり、対応要否の判断は含まない。** 各事項の扱いは本 PR とは別に決める。

行番号はいずれも `9de2c6f` 時点のもの。

---

## 1. `src/main` が JUnit の INTERNAL API に依存している

### 何が起きているか

`TestEventDispatcherExtension` が、JUnit が内部用と宣言している API を使っている。

| 箇所 | 内容 |
|---|---|
| `src/main/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtension.java:13` | `import org.junit.platform.commons.util.ReflectionUtils;` |
| 同 `:256-258` | `ReflectionUtils.findFields(testInstance.getClass(), isInjectionTarget, ReflectionUtils.HierarchyTraversalMode.BOTTOM_UP)` |

`src/test` にも 1 か所ある。

| 箇所 | 内容 |
|---|---|
| `src/test/java/nablarch/test/junit5/extension/http/BasicHttpRequestTestExtensionTest.java:6` | `import org.junit.platform.commons.util.ReflectionUtils;` |
| 同 `:32` | `ReflectionUtils.findMethod(BasicHttpRequestTestTemplate.class, "getBaseUri")` |

### `@API` の宣言

本モジュールが解決する `junit-platform-commons` は **1.11.0**（compile スコープ。`mvn -o dependency:tree` で確認。
版は `pom.xml:26-32` が import する `junit-bom` 5.11.0 由来）。同 jar を `javap -v` に掛けて得た値は次のとおり。

| クラス | クラス単位の `@API` |
|---|---|
| `org.junit.platform.commons.util.ReflectionUtils` | `status = INTERNAL, since = "1.0"` |
| `org.junit.platform.commons.support.ReflectionSupport` | `status = MAINTAINED, since = "1.0"` |

`ReflectionSupport#findFields` にメソッド単位の `@API` 上書きはないため、クラス単位の MAINTAINED が適用される。

### 置き換え先が存在する

`ReflectionSupport` に同一シグネチャのメソッドがある（`javap -cp junit-platform-commons-1.11.0.jar` で確認）。

```
public static java.util.List<java.lang.reflect.Field> findFields(
    java.lang.Class<?>,
    java.util.function.Predicate<java.lang.reflect.Field>,
    org.junit.platform.commons.support.HierarchyTraversalMode);
```

`src/test` 側の `findMethod` にも対応するものがある
（`ReflectionSupport#findMethod(Class<?>, String, Class<?>...)`）。

置き換えに必要な変更は次のとおり。

- `src/main` 1 ファイル — import 2 行（`ReflectionSupport` と `HierarchyTraversalMode`）と呼び出し 1 か所。
  `HierarchyTraversalMode` は `support` パッケージでは**トップレベルのクラス**であり、`util` 側のような入れ子 enum ではない。
  そのため呼び出しは `ReflectionUtils.HierarchyTraversalMode.BOTTOM_UP` から `HierarchyTraversalMode.BOTTOM_UP` に変わる
- `src/test` 1 ファイル — import 1 行と呼び出し 1 か所

### 実測した事実 2 点

**(a) 振る舞いは変わらない。** `ReflectionSupport#findFields` は `ReflectionUtils#findFields` に委譲しているだけである。
`javap -c` で得たバイトコードは次のとおり。

```
 0: aload_2
 1: ldc            // String HierarchyTraversalMode must not be null
 3: invokestatic   // Method org/junit/platform/commons/util/Preconditions.notNull:(...)
 6: pop
 7: aload_0
 8: aload_1
 9: aload_2
10: invokevirtual  // Method org/junit/platform/commons/support/HierarchyTraversalMode.name:()Ljava/lang/String;
13: invokestatic   // Method org/junit/platform/commons/util/ReflectionUtils$HierarchyTraversalMode.valueOf:(...)
16: invokestatic   // Method org/junit/platform/commons/util/ReflectionUtils.findFields:(...)
19: areturn
```

**この委譲構造のため、置き換えても JUnit の別バージョン上で動作する保証にはならない。**
実際に呼ばれるコードは同じであり、動作するかどうかは実際に動かして確かめる以外にない。

**(b) INTERNAL API が壊れた実績はない。** ローカルに存在する 4 バージョンで
`ReflectionUtils.findFields(Class, Predicate, ReflectionUtils$HierarchyTraversalMode)` のシグネチャを
`javap` で比較したところ、すべて同一だった。

| `junit-platform-commons` | `ReflectionUtils#findFields` | `ReflectionSupport#findFields` |
|---|---|---|
| 1.3.1 | あり | **なし** |
| 1.8.2 | あり | あり |
| 1.9.3 | あり | あり |
| 1.11.0 | あり | あり |

`ReflectionSupport#findFields(Class, Predicate, HierarchyTraversalMode)` は 1.3.1 には存在しない。
本モジュールは junit-bom 5.11.0（commons 1.11.0）を固定しているため、現状この差は問題にならない。

### 差分の性質

置き換えによって変わるのは、JUnit 側が将来この API を壊してよいかどうかという**契約**の一点である。
INTERNAL / MAINTAINED という語の定義は apiguardian の javadoc に依るが、
本調査では一次情報を開いて確認していない（**未確認**）。アノテーションの値が INTERNAL と MAINTAINED であることは
上記のとおり jar から確認済みである。

### 本 PR の対象外とした理由

本 PR が対象とする不具合（`TestRule` がテストメソッドの実行を包まないこと）とは独立しており、
不具合対応に範囲を絞るため。design.md §1.4 の scope 外にあたる。

### 出所

タスク #4 の Design エキスパートレビューが発見した。`checks/4.md:224` に当時の記録がある
（同記録は変更量を「import 1 行と呼び出し 1 か所」と書いているが、上記のとおり実際は import 2 行になる。
検証時点の記録として書き換えていない）。
