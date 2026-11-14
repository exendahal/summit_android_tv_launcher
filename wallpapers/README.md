# Wallpapers

Summit downloads the wallpapers in this folder when the TV is online, so new ones reach every TV without making the
app bigger. They appear in **Settings › Theme › Wallpaper** after the built-in ones; a wallpaper is downloaded the
first time someone picks it and then works offline.

## Add a wallpaper
Mountains are the theme: every wallpaper should have mountains as its main subject.

1. Add the image to `images/<id>.png` (or `.jpg`, `.webp`):
   - exactly **1920 × 1080** pixels (16:9)
   - at most **600 KB**; flat illustrations compress well as PNG, photos as JPG or WebP
   - keep the middle-left area calm: the clock and dates sit there, over a darkening tint
2. Add a thumbnail to `thumbs/<id>.png` (or `.jpg`, `.webp`): exactly **384 × 216** pixels, at most **60 KB**.
3. Add an entry to `index.json`:
   ```json
   {
     "id": "glacier",
     "name": "Glacier",
     "names": { "ne": "हिमनदी" },
     "author": "Your name",
     "image": "images/glacier.png",
     "thumb": "thumbs/glacier.png"
   }
   ```
   - `id`: lowercase letters, digits and `-`; unique; never change it once published, as TVs remember it
   - `name`: the English name shown in Settings
   - `names`: the name in every other language the app offers, keyed by the codes in
     [`locales_config.xml`](../app/src/main/res/xml/locales_config.xml) (`ne`, `hi`, `zh-Hans`, `es` and so on).
     All of them are required; copy an existing entry as a template. Machine translation is fine as a start
   - `author`: you, or the artist
4. Open a pull request. The *Wallpapers check* workflow validates the entry, every language name, sizes and file sizes.

Only submit work you made or that you may share under the project's [MIT License](../LICENSE).

## Remove or replace a wallpaper
Remove its entry and files, or replace the files under the same `id`. TVs that already downloaded it keep their
copy until someone picks another wallpaper.
