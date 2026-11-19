# Mobile Stream Server V4 — autonome, sans Termux

Cette V4 est un projet source Android. Elle n'est pas encore un APK compilé.
Le but est que l'APK final embarque MediaMTX Android ARM64 : au premier lancement, l'application extrait le moteur dans son stockage privé et démarre RTMP, SRT, l'API, les métriques et le Dashboard Web.

## Ce que l'APK fait

- MediaMTX embarqué dans l'APK (pas de téléchargement de moteur à l'installation).
- Extraction automatique du binaire dans le stockage privé de l'application.
- Service Android au premier plan avec notification persistante.
- RTMP TCP 1935, SRT UDP 8890.
- API et métriques liées uniquement à 127.0.0.1.
- Dashboard Web local sur 8080.
- Dashboard natif Android.
- Détection de l'adresse IPv4 des interfaces réseau actives.
- Débits entrants/sortants, flux actifs, lecteurs, RAM et état thermique Android.
- Vérification de nouvelle version via GitHub Releases.
- Démarrage manuel (pas de démarrage automatique au boot).
- Aucun enregistrement.

## IMPORTANT : aucun Termux requis sur le téléphone pour utiliser l'APK

Le moteur MediaMTX est compilé par GitHub Actions dans le cloud, puis inclus dans l'APK.
L'utilisateur final n'installe ni Termux ni Python.

## Générer l'APK sans Termux

1. Créer un dépôt GitHub personnel (par exemple `MobileStreamServer`).
2. Envoyer tout le contenu de cette archive dans ce dépôt.
3. Dans GitHub, ouvrir l'onglet **Actions**.
4. Autoriser les GitHub Actions si GitHub le demande.
5. Lancer **Build Mobile Stream Server V4** avec **Run workflow**.
6. Attendre la fin du job.
7. Dans le résultat du workflow, télécharger l'artifact `MobileStreamServer-V4-debug`.
8. Extraire `app-debug.apk` et l'installer sur l'Infinix.
9. Accepter l'autorisation d'installation d'applications provenant de cette source si Android la demande.

Le workflow :
- récupère les sources MediaMTX v1.21.4 ;
- compile un exécutable `GOOS=android GOARCH=arm64` ;
- place le binaire dans `app/src/main/assets/mediamtx` ;
- compile l'APK avec JDK 21, Gradle 9.6.1 et AGP 9.3.1.

La compilation MediaMTX suit la procédure officielle `go generate ./...` puis `go build`, avec les variables de cross-compilation Go. Voir https://mediamtx.org/docs/misc/compile.

## Vérification des mises à jour

Pour activer le contrôle des releases dans l'application, renseigner le dépôt GitHub dans `app/build.gradle` :

```groovy
buildConfigField 'String', 'UPDATE_REPO', '"TON_COMPTE_GITHUB/MobileStreamServer"'
```

Remplacer `TON_COMPTE_GITHUB/MobileStreamServer` par le propriétaire et le nom réels du dépôt, puis reconstruire l'APK.

Le contrôle compare le `tag_name` de la dernière GitHub Release à la version de l'application. Lorsqu'une version plus récente existe, l'application ouvre la page de release. Android exige toujours une confirmation de l'utilisateur pour installer une mise à jour : aucune application ordinaire ne peut installer silencieusement son propre APK.

Pour publier une mise à jour :
1. Mettre à jour `versionCode` et `versionName` dans `app/build.gradle`.
2. Créer et pousser un tag Git, par exemple `v4.1`.
3. Le workflow compile et publie l'APK dans GitHub Releases.
4. Les installations configurées sur ce dépôt détectent la nouvelle release au prochain contrôle.

## Construire sur le téléphone (optionnel)

Le projet peut être ouvert dans Code on the Go, qui est un IDE Android distinct. L'application installée n'a pas besoin de Code on the Go. Pour éviter Termux et la compilation Go sur le téléphone, il est recommandé d'utiliser le workflow GitHub Actions ci-dessus.

Code on the Go : https://appdevforall.org/code-on-the-go/

## Première exécution sur Infinix / Android 14

1. Installer et ouvrir l'APK.
2. Autoriser les notifications.
3. Appuyer sur START SERVER.
4. Autoriser le fonctionnement en arrière-plan / batterie sans restriction si souhaité.
5. Activer manuellement le hotspot Android si le PC doit se connecter directement au téléphone.
6. Lire l'adresse IPv4 affichée dans l'application.
7. Depuis le PC sur le même réseau : `http://IP_DU_TELEPHONE:8080`.

## URLs

RTMP publish/read :
`rtmp://IP_DU_TELEPHONE:1935/live/camera1`

SRT publish :
`srt://IP_DU_TELEPHONE:8890?streamid=publish:live/camera1`

SRT read :
`srt://IP_DU_TELEPHONE:8890?streamid=read:live/camera1`

## Limites Android et sécurité

- Le hotspot doit être activé par l'utilisateur ; les restrictions Android/OEM peuvent empêcher son activation silencieuse.
- L'adresse IP est détectée parmi les interfaces IPv4 actives. En cas de Wi-Fi + données mobiles, vérifier que c'est l'interface du réseau auquel le PC est connecté.
- Le Dashboard Web V4 est en HTTP sans authentification : accès uniquement sur un réseau local de confiance. Ne pas exposer le port 8080 à Internet.
- L'API et les métriques MediaMTX restent sur localhost.
- Le statut thermique est le niveau thermique Android, pas une température en degrés Celsius.
- Le service utilise un Foreground Service `specialUse` avec notification permanente. Android 14 et les politiques OEM peuvent limiter le fonctionnement en arrière-plan.
- Démarrage au boot désactivé conformément au choix de configuration.
- Cette archive contient le code source et le pipeline de compilation ; elle ne contient pas encore l'APK final. Le workflow GitHub génère le binaire MediaMTX Android et l'APK.


## V5 — intégration du moteur Android
Le binaire MediaMTX est maintenant placé dans `app/src/main/jniLibs/arm64-v8a/libmediamtx.so` afin qu’Android l’extraie dans `nativeLibraryDir`, plutôt que de tenter de l’exécuter depuis `filesDir`. Le workflow vérifie sa présence dans l’APK avant de publier l’artefact.
