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
- Ustawienia domyślne dla Polski: Warszawa, stopnie Celsjusza, złotówki, język polski.

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
- **Aktualizacje bez odinstalowywania:** dodaj sekret `KEYSTORE_B64` z kluczem podpisu (szczegóły na górze
  `.github/workflows/apk.yml`). Bez niego każda nowa wersja ma inny podpis: przed aktualizacją przełącz na
  **Fabryczny** i odinstaluj starą wersję.
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
