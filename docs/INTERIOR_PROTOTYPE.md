# リアル寄りの内装試作

Minecraft 26.3 / Fabric、Java 25。

通常ワールド全体に適用する描画の開発は [WORLD_RENDERING.md](WORLD_RENDERING.md) に記載。
以下の廊下コマンドは内装比較用で、連続サーフェス描画の使用に必要なものではない。

## 試す

1. `bash gradlew runClient` で起動する。
2. チートを有効にした試作用ワールドに入る。
3. 開けた場所で `/horror demo` を実行する。
4. 廊下へ移動する。照明は消えているので、渡される懐中電灯を手に持って照らす。
   懐中電灯は右クリックでオン/オフ。ドアは右クリックで開閉。F1でHUD・手・選択枠を隠す。
5. `/horror return` で実行前の場所へ戻る。

生成前に5×5×19ブロックの全範囲が空気で、ワールド境界内であることを確認する。
プレイヤーの約32ブロック上（高さ制限付近では下げる）に生成する。
元のブロックの上書きは行わない。生成した建物は戻った後も残る。
戻り先はサーバーのメモリ内のみなので、終了する前に戻ること。
時刻、天気、ゲームモード、視野角、ユーザー設定は変更しない。
生成後の移動に失敗した場合は生成位置を表示し、コマンドは失敗を返す。
帰還に失敗した場合は戻り先を保持するので `/horror return` を再試行できる。

## 内容

- 幅3m、奥行17m、天井高3mの通路。奥に開閉できる管理用ドア。
- 512pxのコンクリート素材。縁取りをなくし、回転バリエーションで反復を軽減。
- 直径約25cmの八角断面の配管。衝突判定は外接する細い箱で近似。
- 厚さ18.75cmのドア。Minecraft標準の開閉処理と衝突形状を利用。
- 厚さ12.5cmの天井照明。通常のブロック光源を使用。`lit=false` で消灯状態になり、廊下では消灯で配置。
- 懐中電灯 `horror:flashlight`。メイン・オフハンドのどちらかに持つと、視線の先24ブロック以内の
  当たった面の手前に見えない光源（明るさ13）を置き、視線に合わせて移動する。
  光源は空気の場所にだけ置き、既存ブロックは上書きしない。持つのをやめる・オフにする・
  ログアウトすると消える。サーバー停止時も消し、クラッシュ等で残った光源は2秒以内に自動で消える。
  `/horror demo` 実行時、持っていなければインベントリに1本追加する。
- 高さ12.5cm、厚さ約4.7cmの巾木。

`/give @s horror:concrete` などで各ブロックを取得できる。
ID: `concrete`, `floor`, `pipe`, `ceiling_light`, `skirting`, `service_door`, `flashlight`。
配管・照明はZ方向、巾木は西側の壁用の固定形状。ドアは配置方向に対応する。
試作用のためレシピ・ドロップ・クリエイティブタブへの追加はまだない。

## 現段階の限界

これは内装の形と素材を比較するための試作。写実的な陰影、PBR、動的な影、
独自の移動・音響・UIは未実装。懐中電灯の光は円錐状ではなく、照らした地点を中心に
ブロック単位で全方向へ広がる。光の伝播は標準のブロック単位。
コンクリートの床と壁は同じ素材で、テクスチャの完全な継ぎ目消去は実機で要調整。
内装の素材・モデル自体は既存の通常ブロックを置き換えない。
別途、通常ワールドの連続サーフェス描画が不透明な立方体モデルに適用される。

## 素材と生成

`src/main/resources/assets/horror/textures/block/concrete.png`

内蔵 image_gen で生成した画像を512×512へ縮小。生成プロンプト:

> Use case: photorealistic-natural. Asset type: seamless square game albedo texture, 1024x1024. Flat orthographic scan of aged indoor poured concrete, subtle warm gray, fine pores and understated irregular mottling. Seamlessly tileable horizontally and vertically, even neutral lighting, no baked shadows, no perspective, no objects, no text, no borders, no panel lines, no strong large cracks. Realistic fine surface, low contrast so tiling will not stand out in a horror corridor. Save generated image for use in the local game project.

金属・照明の素材はMinecraft標準素材を参照。
モデルの再生成は `python3 tools/generate_interior_assets.py`。
Minecraft 26.3 のローカルGradleキャッシュが必要。生成済みJSONは同梱しているので
通常のビルドでPythonを実行する必要はない。

## 確認状況

- `bash gradlew build` 成功（テストスイートはまだない）。
- 開発クライアント起動、Mod初期化、テクスチャアトラス生成まで確認。
- 追加モデル・テクスチャの読み込みエラーなし。
- アセットJSON全27ファイルの構文・モデル／テクスチャ参照先を確認。
- ドア全32状態のモデル割り当て・回転が標準ドアの設定と一致することを確認。
- 開発用の仮アカウントによるRealms認証エラーは発生する。
- UI操作ツールから開発クライアントを取得できなかったため、廊下内の目視確認、
  コマンド実行、ドア開閉・移動の実機確認は未実施。

### Computer Useでの再確認（2026-09-24）

- Computer Useスキルのラッパーから初期化を試みたが、
  `Computer Use could not load @oai/sky from the cua_node runtime` で失敗。
  `nodeRepl.env.NODE_REPL_NODE_MODULE_DIRS` が未設定で、画面取得前に停止した。
- Minecraftへの操作は行えておらず、以下は引き続き未確認。
  - `/horror demo` の生成・廊下への移動と `/horror return` の帰還。
  - 廊下内の素材、継ぎ目、配管、照明、巾木の目視確認。
  - ドアの右クリック開閉、衝突判定、開いたドアの通過。
  - F1でHUD・手・選択枠を隠した状態の表示。
- 再開にはComputer Useのランタイムが利用可能なセッションが必要。

### ランタイム修復調査（2026-09-24）

- `@oai/sky` 本体は `/Applications/ChatGPT.app/Contents/Resources/cua_node/lib/node_modules`
  に存在し、`~/.codex/config.toml` のMCP設定にも探索先が記載されている。
  ただし、このセッションの `nodeRepl.env` には渡っていない。
- ラッパーに探索先を一時補完すると `process is not defined` が発生。
  環境変数参照を一時的に補っても、画面取得は
  `Computer Use requires nodeRepl.createElicitation` で停止した。
- 探索先の欠落だけでなく、実行ホストに必要な承認連携APIがない。
  試験用のラッパー変更は復元し、REPLもリセットした。Minecraftは未操作。
- 対応するComputer Useホスト環境で再検証が必要。設定変更や再起動だけで
  解消することは確認できていない。

### Computer Useでの画面取得再試行（2026-09-24）

- 今回のセッションでは `node_repl` から `@oai/sky` の読み込みに成功。
  過去の初期化エラーは再現せず、Minecraft Launcherの画面画像と
  アクセシビリティ情報を取得できた。
- `bash gradlew runClient` を実行し、ログでMinecraft 26.3、Mod初期化、
  SDLウィンドウ作成、サウンド初期化、テクスチャアトラス生成まで確認。
- ただし `get_app_state({app: "Minecraft"})` はMinecraft Launcherを取得する。
  `java` と、起動ログに記載されたApp ID `com.mojang.minecraft` は
  `Invalid app` となった。読み込み後の `list_apps()` にも開発クライアントは
  表示されず、ゲーム本体の画面取得・操作には至っていない。
- ワールド内の操作は行っていない。生成・移動・帰還、素材と各部材の目視、
  ドア開閉・衝突・通過、F1表示はすべて引き続き未確認。
- 次回はComputer Useで開発クライアント本体を取得できることを確認してから
  上記の実機検証を再開する。
