# Gallery アプリ仕様書

対象バージョン: 2.0.14  
文書バージョン: 2.0.14  
最終更新: 2026-09-14

## 2.0.14で確認した仕様

- アプリ内更新はAPKの`content://` URIを読み取り権限付きで端末のインストーラーへ渡す。
- `ACTION_INSTALL_PACKAGE`を優先し、端末のパッケージインストーラーが`ACTION_VIEW`のみ登録している場合は`ACTION_VIEW`へフォールバックする。
- Android 11以降のパッケージ可視性確認用に、APK MIME typeのIntentを`<queries>`へ登録する。
