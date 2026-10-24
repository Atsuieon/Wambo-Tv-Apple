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

### 1. Pobierz plik APK

Każda zmiana w tym repozytorium sama buduje gotowy plik. Na komputerze (zalogowany na GitHubie) otwórz
**Releases → „Free My TV dla Wanbo” → `FreeMyTV-Wanbo.apk`** i pobierz go. Repozytorium jest prywatne, więc
projektor nie pobierze go sam: skopiuj plik na **pendrive**.

### 2. Zainstaluj na projektorze

1. Na projektorze zainstaluj ze Sklepu Play menedżer plików, np. **File Commander** albo **X-plore**.
2. Włóż pendrive do projektora, otwórz w menedżerze plik `FreeMyTV-Wanbo.apk` i wybierz **Zainstaluj**.
3. Gdy projektor zapyta o instalowanie z nieznanych źródeł, zezwól na to menedżerowi plików.
4. Jeśli pojawi się Google Play Protect („aplikacja zablokowana”), wybierz **Więcej szczegółów → Zainstaluj mimo to**.

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

- **Polecane filmy i seriale:** załóż darmowy klucz na [themoviedb.org](https://www.themoviedb.org/settings/api)
  i dodaj go w repozytorium jako sekret `TMDB_KEY` (Settings → Secrets and variables → Actions). Nie wklejaj go
  na czacie ani w plikach.
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
