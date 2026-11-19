# Fake GPS

Application Android de simulation de position GPS (« mock location ») avec prise en charge du déplacement.

## Fonctionnalités

- Carte OpenStreetMap (osmdroid) : touchez la carte pour ajouter des points de passage.
- **Traces** : enregistrer le trajet sous un nom, retrouver/charger/supprimer les traces enregistrées,
  importer un fichier GPX (`trkpt`, sinon `rtept`, sinon `wpt`) ou exporter le trajet en GPX.
- **Téléporter** : fixe la position simulée sur le dernier point ajouté.
- **Se déplacer** : suit le trajet point par point, modifiable en cours de route.
- **Vitesse v(t)** : nombre ou expression mathématique recalculée chaque seconde (km/h, sans maximum,
  valeurs négatives ramenées à 0). Variables : `t` (secondes depuis le départ) et `d` (mètres parcourus).
  - Opérateurs : `+ - * / % ^ < > <= >=` ; constantes `pi`, `e`.
  - Fonctions : `sin cos tan asin acos atan atan2 sinh cosh tanh sqrt abs exp ln log2 log10 floor ceil round
    sign min max pow mod rand(a; b) if(condition; a; b)`.
  - Exemples : `30 + 20*sin(t/10)`, `min(5*t; 130)`, `if(t < 60; 5; 15)`, `rand(40; 60)`.
- **Boucler le trajet** : aller-retour avec deux points, circuit fermé avec trois points ou plus.
- Cap (bearing) et vitesse renseignés dans les positions émises.
- Simulation des fournisseurs `gps`, `network` et `fused` (Android 12+).
- Service de premier plan avec notification et bouton « Arrêter ».
- Le trajet, la vitesse et la position de la carte sont mémorisés.

## Utilisation

1. Installez l'APK (artefact de la GitHub Action ou release).
2. Activez les **options pour les développeurs** (7 appuis sur « Numéro de build »).
3. Dans les options pour les développeurs, choisissez **Fake GPS** dans
   « Sélectionner une application de position fictive ».
4. Ouvrez l'application, placez vos points et lancez « Téléporter » ou « Se déplacer ».

## Compilation

```bash
./gradlew assembleDebug
```

Prérequis : JDK 17 et SDK Android (compileSdk 35). minSdk 24 (Android 7.0).

## Intégration continue

Le workflow `.github/workflows/android.yml` exécute les tests unitaires et compile les APK debug et release
à chaque push et pull request ; les APK sont disponibles en artefacts. Pousser un tag `v*`
(par exemple `v1.0`) crée une release GitHub contenant l'APK.
