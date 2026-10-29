# Free My TV dla Wanbo Mozart 1 Pro

Nowy, szybki ekran główny bez reklam dla projektora **Wanbo Mozart 1 Pro** (wersja z Android TV 11 i z Google TV).
Oparty na otwartym projekcie [Free My TV](https://github.com/simbasjungles/free-my-tv) (licencja MIT), dostosowany do projektora.

- Bez reklam i sponsorowanych rzędów.
- Twoje aplikacje (Netflix, YouTube, Prime Video, Disney+…) na szklanej półce, w kolejności, jaką wybierzesz.
- Pogoda dla Twojego miasta, jasny wygląd w dzień i ciemny po zachodzie słońca.
- Wygaszacz ekranu z dużym zegarem i Twoimi zdjęciami.
- Menu po polsku.
- **Bez roota. Nic nie jest odinstalowywane.** Jeden przycisk przywraca fabryczny ekran.

## Co jest zmienione specjalnie dla Wanbo

- **Fabryczny ekran Wanbo zostaje włączony.** Na projektorach autofocus, automatyczna korekcja obrazu (keystone)
  i omijanie przeszkód mogą działać w aplikacji producenta, więc jej nie wyłączamy. Free My TV tylko przejmuje
  przycisk Home.
- **Żadna aplikacja z „wanbo”, „projector”, „keystone”, „focus” w nazwie nie zostanie wyłączona.**
- **Skrót do menu projektora** w dolnej szufladzie: otwiera fabryczny ekran Wanbo, gdzie są ustawienia projekcji.
- **Wersja z Google TV** ma w środku wbudowaną przystawkę Skyworth „4K Google TV Stick” (Android 14). Jej aplikacje
  systemowe (połączenie z projektorem, aktualizacje, parowanie pilota, przyciski pilota) są na liście chronionych.
- Ustawienia domyślne: pogoda ze Szczecina, stopnie Celsjusza, złotówki, język polski.
- **Zdjęcia z telefonu:** przycisk u góry ekranu pokazuje kod QR. Zeskanuj go telefonem (w tej samej sieci Wi-Fi),
  wybierz zdjęcia i gotowe: pojawią się w tle ekranu i w wygaszaczu. Odbieranie działa tylko, gdy ten ekran jest
  otwarty na projektorze, a adres jest za każdym razem inny.
- Pogrubiona godzina i dźwięk nawigacji „miękka kropla”.
- **Przyciski u góry** (domyślnie): HDMI, Zdjęcia z telefonu, Wi-Fi dla gości, Moonlight, Ustawienia.
  „Telewizję” można dodać z powrotem w Free My TV → Przyciski u góry.
- **Wi-Fi dla gości:** duży kod QR, który goście skanują aparatem telefonu i od razu łączą się z siecią. Nazwa
  sieci jest widoczna, hasło ukryte. Nazwę i hasło wpisujesz na projektorze (OK na tym ekranie); zostają tylko w
  ustawieniach aplikacji, nigdy w kodzie.
- **Moonlight:** gry z komputera (Sunshine na PC). Przycisk otwiera Moonlight, a jeśli go nie ma, jego stronę w
  Sklepie Play.
- **Półka aplikacji się zapętla:** w prawo za ostatnią aplikacją jest pierwsza, w lewo przed pierwszą ostatnia.
- **Za małe zdjęcia z telefonu** (poniżej 640 pikseli na krótszym boku): telefon mówi to od razu i ich nie wysyła,
  a ekran z kodem QR pokazuje, ile pominięto.
- **Moje zdjęcia** (Free My TV → Ustawienia ekranu → Moje zdjęcia → OK): miniatury wszystkich zdjęć. Przy każdym
  widać, czy jest na ekranie głównym i w wygaszaczu, a OK otwiera je w dużym podglądzie, gdzie włączysz je osobno
  tu i tam, ustawisz jako jedyne tło albo usuniesz. Na górze: rozmycie tła (0–100%), jak często zmienia się tło
  i przycisk do wysyłania z telefonu.

## Instalacja (ok. 15 minut)

### 1–2. Pobierz i zainstaluj na projektorze (bez komputera)

Każda zmiana w tym repozytorium sama buduje gotowy plik `FreeMyTV-Wanbo.apk` (zakładka **Releases**).

1. Na projektorze zainstaluj ze Sklepu Play aplikację **Downloader** (autor: AFTVnews).
2. Otwórz ją i wpisz pilotem adres:

   ```
   https://github.com/Atsuieon/Wambo-Tv-Apple/releases/latest/download/FreeMyTV-Wanbo.apk
   ```

3. Po pobraniu wybierz **Zainstaluj**. Gdy projektor zapyta o instalowanie z nieznanych źródeł, zezwól na to
   aplikacji Downloader.
4. Jeśli pojawi się Google Play Protect („aplikacja zablokowana”), wybierz **Więcej szczegółów → Zainstaluj mimo to**.

Wolisz pendrive? Pobierz ten sam plik na komputerze, skopiuj go na pendrive i otwórz na projektorze w menedżerze
plików (np. **File Commander** albo **X-plore** ze Sklepu Play).

### 3. Włącz tryb programisty (potrzebny do przełączenia ekranu)

Free My TV przełącza ekran główny przez wbudowane w projektor narzędzie ADB, dlatego trzeba je raz włączyć:

- **Android TV 11:** Ustawienia → Preferencje urządzenia → Informacje → kliknij 7 razy **Kompilacja**.
  Potem Ustawienia → Preferencje urządzenia → **Opcje programisty** → włącz **Debugowanie USB**.
- **Google TV:** Ustawienia → System → Informacje → kliknij 7 razy **Kompilacja systemu operacyjnego Android TV**.
  Potem Ustawienia → System → **Opcje programisty** → włącz **Debugowanie USB**.

### 4. Przełącz ekran

Otwórz aplikację **Free My TV** → **Ekran główny** → **Free My TV**. Projektor zapyta *„Zezwolić na debugowanie
USB?”*: zaznacz **Zawsze zezwalaj** i potwierdź. Gotowe: przycisk Home na pilocie otwiera nowy ekran.

> Jeśli aplikacja napisze, że nie może połączyć się z ADB, projektor ma zamknięty port 5555. Wtedy raz, z komputera
> w tej samej sieci Wi-Fi: `adb connect <IP projektora>:5555` (albo sparuj przez *Debugowanie bezprzewodowe*),
> potem `adb tcpip 5555`, i spróbuj jeszcze raz.

## Powrót do fabrycznego ekranu

**Free My TV → Ekran główny → Fabryczny.** Wszystko wraca dokładnie tak, jak było. Jeśli nowy ekran zawiesi się
3 razy w ciągu 5 minut, projektor sam wróci do fabrycznego.

Chcesz odinstalować? Najpierw przełącz na **Fabryczny**, potem Ustawienia → Aplikacje → Free My TV → Odinstaluj.

## Opcjonalnie

- **Polecane filmy i seriale:** potrzebny jest darmowy klucz z [themoviedb.org](https://www.themoviedb.org/settings/api).
  Repozytorium jest publiczne, więc gotowy plik z Releases go nie ma (każdy mógłby go wyciągnąć). Żeby mieć
  polecane, zbuduj własną wersję na komputerze: wpisz klucz po `tmdb_key=` w `config.properties` i uruchom
  `./build.sh` (szczegóły w [README-free-my-tv.md](README-free-my-tv.md)). Nie wklejaj klucza na czacie ani do repozytorium.
  Bez klucza wszystko inne działa.
- **Aktualizacje bez odinstalowywania:** dodaj sekret `KEYSTORE_B64` z kluczem podpisu (Settings → Secrets and
  variables → Actions → New repository secret). Bez niego każda nowa wersja ma inny podpis: przed aktualizacją
  przełącz na **Fabryczny** i odinstaluj starą wersję. Z nim nową wersję instalujesz po prostu na starą.
- **Twoje miasto:** zmień `latitude`, `longitude` i `city` w `config.example.properties` albo w aplikacji
  (Ustawienia ekranu).

## Dalsze dopasowanie do Twojego projektora

Żeby wyłączyć też reklamowe aplikacje Wanbo i dodać przyciski pilota, potrzebna jest lista aplikacji z Twojego
egzemplarza. Z komputera z `adb` uruchom (nic nie zmienia, tylko czyta):

```bash
adb connect <IP projektora>:5555
adb shell getprop ro.product.brand; adb shell getprop ro.product.model; adb shell getprop ro.build.version.release
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell pm list packages -s
```

i wklej wynik Claude'owi. Szczegóły dla AI są w [CLAUDE.md](CLAUDE.md).

Oryginalny opis projektu (po angielsku): [README-free-my-tv.md](README-free-my-tv.md).
