# NFC Pocket

NFC Pocket est une petite application Android native qui transforme temporairement un téléphone compatible en tag NFC NDEF. Le contenu est lu par un autre téléphone directement via NFC, sans compte, serveur, télémétrie ou connexion réseau.

## Fonctionnement NFC

L’application utilise Android Host Card Emulation (`HostApduService`) et implémente directement un tag NFC Forum Type 4 en lecture seule :

- AID NDEF standard : `D2760000850101` ;
- sélection de l’application NDEF ;
- sélection et lecture du Capability Container `E103` ;
- sélection et lecture du fichier NDEF `E104` ;
- commandes `SELECT` et `READ BINARY`, avec réponses découpées selon le `Le` demandé ;
- contenu NDEF dynamique conservé localement jusqu’au bouton **Arrêter**.

L’implémentation est autonome et n’ajoute aucune bibliothèque NFC externe. Les projets Apache-2.0 [LuigiVampa92/ndef-emulator](https://github.com/LuigiVampa92/ndef-emulator) et [MichaelsPlayground/NfcHceNdefEmulator](https://github.com/MichaelsPlayground/NfcHceNdefEmulator) ont été étudiés comme références d’interopérabilité, mais aucun de leur code n’est embarqué.

## Contenus pris en charge

- URL (`https://…`) ;
- téléphone (`tel:`) ;
- SMS (`sms:` avec message optionnel) ;
- email (`mailto:` avec sujet et corps optionnels) ;
- localisation (`geo:`) ;
- texte NDEF UTF-8 ;
- contact vCard 3.0 (`text/vcard`).

Les modèles de payload sont séparés de l’encodage NDEF afin de faciliter l’ajout de nouveaux types.

L’icône NFC utilisée dans l’application et son icône adaptative provient de [Google Material Design Icons](https://github.com/google/material-design-icons/tree/master/src/device/nfc/materialicons), sous licence Apache-2.0. Elle est conservée en vector drawable sur fond transparent ; l’icône adaptative ajoute uniquement un fond vert uni.

## Données et interface

Les éléments, favoris et dates d’utilisation sont stockés uniquement sur l’appareil avec DataStore Preferences et un JSON compact. Les 100 éléments non favoris les plus récents sont conservés ; les favoris ne sont jamais supprimés automatiquement. Le thème Compose Material 3 suit le mode clair/sombre du système et utilise les couleurs dynamiques à partir d’Android 12.

## Cible de partage Android

NFC Pocket accepte `ACTION_SEND` pour `text/plain` et `text/uri-list`. Un texte commençant par `http://` ou `https://` est interprété comme une URL ; tout autre contenu devient un enregistrement texte. Depuis Chrome : **Partager → NFC Pocket** ouvre directement l’écran d’émulation et ajoute le contenu à l’historique.

## Build exclusivement avec GitHub Actions

Aucune toolchain Android locale n’est nécessaire. Le workflow `.github/workflows/build-apk.yml` se lance à chaque push sur `main` ou manuellement avec **Run workflow**. Il installe Java 17 et le SDK Android sur le runner Ubuntu, puis produit un APK debug installable.

Pour télécharger l’APK :

1. ouvrir l’onglet **Actions** du dépôt ;
2. ouvrir le dernier workflow **Build debug APK** réussi ;
3. descendre jusqu’à **Artifacts** ;
4. télécharger **nfcpocket-apk** puis extraire `app-debug.apk`.

## Compatibilité et limitations

- Android 10 minimum (`minSdk 29`), cible Android 16/API 36 ;
- le téléphone émetteur doit exposer `FEATURE_NFC_HOST_CARD_EMULATION` et avoir le NFC activé ;
- HCE émule une carte ISO-DEP/Type 4, pas la modulation physique de tous les types de tags NFC ;
- le tag est en lecture seule et ne peut pas être réécrit par le lecteur ;
- le comportement de détection NDEF, l’activité écran verrouillé et le routage de l’AID standard peuvent varier selon Android, HyperOS et les restrictions constructeur ;
- une autre application HCE déclarant le même AID peut provoquer un conflit de routage ; NFC Pocket se déclare service préféré pendant que son écran d’émulation est au premier plan ;
- certains téléphones ne lancent pas automatiquement une application pour certains schémas (`geo:`, `sms:`, `mailto:` ou vCard), même si le message NDEF est correctement lu ;
- la proximité téléphone-à-téléphone dépend fortement de la position des deux antennes NFC et les deux appareils ne peuvent pas simultanément agir comme lecteurs.

## Confidentialité

L’application demande uniquement la permission NFC. Elle ne déclare pas la permission Internet et n’envoie aucune donnée hors du téléphone.
