# Do zrobienia

## Za małe zdjęcia z telefonu (poniżej 640 px na krótszym boku)

Dziś takie zdjęcie jest „wysłane”, ale projektor po cichu je pomija.

1. **Komunikat na telefonie.** Strona z kodu QR sprawdza rozmiar zdjęcia przed wysłaniem. Za małe nie jest
   wysyłane, a strona pisze np. „To zdjęcie jest za małe (513 × 386). Potrzeba co najmniej 640 pikseli na
   krótszym boku.” Przy kilku zdjęciach wysyła tylko dobre i mówi, ile pominęła.
   (`PhoneUpload.page()`; dodatkowo sprawdzenie po stronie projektora w `PhoneUpload.upload()`, np. dla HEIC.)
2. **Komunikat na projektorze.** Na ekranie z kodem QR obok „Odebrane zdjęcia: N” także „Pominięte (za małe): N”.
   (`PhoneUploadActivity`.)
