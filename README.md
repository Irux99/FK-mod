# Irux FK-2000

Addon Minecraft Java **1.20.1**, **Forge 47.4.10**, **JDK 17**, mod ID `iruxfk2000`. Le mod ajoute un véhicule antiaérien original avec conduite locale et à distance, radar tactique et missiles à lancement vertical à froid.

**Préversion 0.5.0-preview1 : collisions découpées et carte détaillée.** Huit sections suivent le châssis dans les virages, sans collision dans les grands coins vides. La carte en 128 × 128 montre le relief, les couleurs des biomes et les terrains observés conservés entre les zooms. En mode Coordonnées, cliquer sur la carte sélectionne un point au sol. Les icônes de la télécommande, du missile et du camion sont renouvelées ; la caisse orange est conservée. Le radar affiche aussi les missiles en vol. Cette version d’essai précède la validation complète du pack de performances ; voir [le bilan 0.5](docs/VALIDATION-0.5.md).

**Version 0.4.0 : contrôle distant, radar cartographique et nouveaux effets.** Les camions possédés gardent leurs chunks actifs et les missiles chargent un couloir devant leur trajectoire. Le radar permet de choisir les véhicules SBW, les entités ou des coordonnées, avec salve sur une cible ou un missile par cible sélectionnée. Il affiche le terrain, un balayage tournant et des échos persistants avec zoom. Le lancement possède trois modes de bouchon, une réaction de suspension, des jets de correction arrière, une plume lumineuse et un son 3D qui suit le missile puis s'éteint en fondu. Un camion détruit explose et laisse une carcasse noire.

Le camion fourni utilise ses huit roues et son lanceur à six tubes, avec une texture camouflage 2048 × 2048. Le missile utilise le modèle et la texture fournis. Les archives OBJ/FBX d'origine et les exports 3D réutilisables ne sont pas publiés dans ce dépôt ; ces ressources restent sous licence CGTrader, séparée de la licence MIT du code. Le JAR du jeu contient les ressources nécessaires à son rendu. Une compilation des sources sans ces ressources affiche les modèles de remplacement originaux.

Les sources des modèles, leurs licences signalées et les commandes de conversion sont indiquées dans [IMPORTED-ASSETS.md](docs/IMPORTED-ASSETS.md). Le bilan de cette mise à jour est dans [VALIDATION-0.4.md](docs/VALIDATION-0.4.md). Les anciens documents de validation restent conservés pour l'historique.

## Préparer le projet

Ouvrir ce dossier directement : chaque tâche cloud dispose déjà de son environnement isolé. Aucun worktree Git supplémentaire n'est nécessaire.

Pré-requis : un JDK **17**, une connexion aux dépôts Maven/Forge/Gradle et un client Minecraft pour les essais visuels. Le wrapper Gradle du projet utilise **8.8** ; une installation globale de Gradle n'est pas requise.

```bash
java -version
./gradlew --version
./gradlew --no-daemon build
```

Sous Windows, utiliser `gradlew.bat` à la place de `./gradlew`. Si plusieurs JDK sont installés, définir `JAVA_HOME` vers le JDK 17 et choisir le même JDK dans les paramètres Gradle de l'IDE. Le premier build télécharge Minecraft, Forge et leurs dépendances ; une erreur réseau empêche la préparation et ne doit pas être contournée en désactivant TLS ou les signatures.

Le JAR distribuable, après compilation et réobfuscation réussies, se trouve dans `build/libs/`. Installer le JAR principal ; le JAR `sources` éventuel sert à consulter le code.

Pour préparer ou rafraîchir l'instance cloud Linux x86_64, exécuter `bash scripts/cloud-install.sh`. Ce script active un JDK 17 officiel épinglé, préserve les vérifications des téléchargements, transmet au JVM le proxy réseau fourni et lance le build. Dans chaque nouveau shell cloud, exécuter `source scripts/cloud-env.sh` avant les commandes Gradle ; le proxy est transmis par hôte/port sans copier de credentials, avec le magasin de certificats système.

Dans l'instance cloud préparée, le JDK 17 vérifié se trouve dans `/workspace/toolchains/jdk-17.0.17+10`. Cette localisation est propre à l'environnement ; une installation locale peut utiliser un autre chemin. Le build fixe ForgeGradle **6.0.36** et vérifie l'empreinte de la distribution Gradle.

## IntelliJ IDEA

1. Ouvrir le dossier comme projet Gradle, sans recréer les fichiers de build.
2. Choisir JDK 17 pour le projet et pour la JVM Gradle.
3. Importer/synchroniser Gradle.
4. Exécuter `./gradlew genIntellijRuns`, puis réimporter si nécessaire.
5. Lancer les configurations Minecraft générées ou les tâches `runClient` / `runServer`.

Les commandes de développement sont :

```bash
./gradlew runClient
./gradlew runServer --args '--nogui'
```

Lire et accepter l'EULA Minecraft dans le répertoire de jeu du serveur si vous souhaitez le démarrer. Un environnement sans affichage ne permet pas de valider les animations, la caméra ou les interactions de l'interface. Le noyau du mod peut être développé et démarré sans SBW, déclaré comme dépendance optionnelle. Pour les essais d'intégration en développement, sélectionner explicitement une version : `./gradlew -PsbwVersion=0.8.9-final -PrunDirectory=run/sbw-0.8.9-final runClient`. Les variantes acceptées sont `0.8.9-final`, `0.8.9.1-hotfix` et `0.8.9.2`. Gradle télécharge alors les versions officielles exactes avec leurs dépendances runtime et crée ses copies de travail remappées ; les JAR officiels ne sont pas modifiés. Éviter de déposer simultanément des copies dans `run/mods/`. Sans ce paramètre, SBW reste absent du classpath de développement.

Dans le cloud, l'affichage virtuel se prépare avec `bash scripts/cloud-display-install.sh`. Démarrer ensuite le client avec `bash scripts/cloud-client.sh -PsbwVersion=0.8.9.2`. Le helper lance son propre Xvfb et vérifie sa disponibilité ; il utilise le rendu logiciel et conserve les mondes dans `/workspace/client-smoke`. Si un autre processus occupe l'affichage, choisir un numéro libre avec `IRUX_DISPLAY_NUMBER=92 bash scripts/cloud-client.sh -PsbwVersion=0.8.9.2`. Les signatures Debian et empreintes des paquets sont vérifiées, sans installation système.

## Installer en jeu

Sur le client et sur le serveur :

1. Installer Minecraft 1.20.1 et Forge 47.4.10.
2. Installer la version officielle de Superb Warfare à tester, pour **Minecraft 1.20.1 Forge**.
3. Installer **Kotlin for Forge >=4.11.0**, compatible avec cette version de Minecraft, et les autres dépendances requises par cette distribution SBW. Vérifier celles déjà embarquées dans le JAR pour éviter les doublons.
4. Ajouter le JAR compilé `iruxfk2000` dans `mods/`.
5. Utiliser les mêmes versions et paramètres serveur pour tous les participants.

Les dépendances restent séparées du JAR de l'addon. Les API SBW examinées, les dépendances et les résultats par version sont documentés dans [SBW-COMPATIBILITY.md](docs/SBW-COMPATIBILITY.md). L'intégration radar utilise les types sémantiques des véhicules SBW ; le FK-2000 est une entité Forge autonome. L'interopérabilité avec les systèmes de réparation, de blindage et de munitions de SBW n'est pas encore établie.

## Utilisation

Créer un monde de test en créatif et obtenir les objets `iruxfk2000` depuis l'inventaire ou les commandes `/give`. Les objets enregistrés dans `ModContent` sont notamment `fk2000_deployment`, `remote_controller` et `missile`.

```mcfunction
/give @s iruxfk2000:fk2000_deployment
/give @s iruxfk2000:remote_controller
/give @s iruxfk2000:missile 8
```

- Utiliser `iruxfk2000:fk2000_deployment` sur le sol pour créer le véhicule.
- Interagir avec le véhicule pour prendre le poste de conduite.
- Avec la télécommande en main, maintenir Accroupir et interagir avec son véhicule pour l'associer. L'association et les commandes sont validées côté serveur.
- Faire un clic droit avec la télécommande associée pour ouvrir l'interface tactique.
- Sélectionner un contact radar, puis demander le tir manuel : le lanceur doit terminer sa préparation avant l'éjection.
- Pour recharger, s'accroupir et faire un clic droit sur le camion avec un missile en main, ou utiliser le bouton de rechargement à moins de huit blocs. Le délai par défaut est de 0,8 seconde par missile, avec un son mécanique original.
- En tir automatique, les tubes restent levés pendant l'engagement. Après la préparation initiale, le délai entre tirs est de 1,5 seconde par défaut ; le lanceur se rabaisse quand il n'y a plus de cible valide, de munitions ou lorsque le mode automatique est désactivé.

Le bouton de type de cible, au-dessus du radar, ouvre les réglages. Choisir **Aérien**, **Véhicules SBW**, **Toutes les entités** ou **Coordonnées**. Les alliés et le propriétaire restent protégés ; le mode toutes les entités inclut les joueurs adverses et les créatures, pas les objets au sol. Pour une destination fixe, saisir X/Y/Z puis appliquer. La portée d'engagement se règle séparément du zoom, de 16 à 2 048 blocs par défaut ; la destination par coordonnées peut se trouver jusqu'à 4 096 blocs du camion.

Choisir **Salve sur une cible** pour engager le contact choisi jusqu'à épuisement des missiles ou perte de cette cible. Choisir **Un missile par cible**, cliquer les contacts souhaités sur le radar puis activer le tir automatique : chaque cible marquée reçoit un missile, puis la sélection est consommée. Le lanceur ne passe pas sur un contact non sélectionné. Changer ce mode annule la préparation en cours et désactive le tir automatique.

La molette et les boutons **+ / −** règlent le zoom de la carte, indépendamment de la portée de tir. La ligne de balayage effectue un tour en six secondes ; les points se rééclairent à son passage et restent visibles et sélectionnables entre deux passages. La carte affiche les couleurs du terrain observé. Les régions inconnues restent sombres ; la consultation de la carte ne charge pas à elle seule des milliers de chunks.

Dans les mêmes réglages, choisir les bouchons **Éjectés**, **Articulés** ou **Aucun**. La suspension arrière se comprime brièvement à chaque éjection. Après destruction, retirer sa carcasse en s'accroupissant et en l'utilisant avec le pied-de-biche SBW.

Dans la **Vehicle Assembling Table** de SBW, ouvrir l'onglet **FK** (la recette figure aussi dans Défense). Réunir 192 lingots de fer, 64 de cuivre, 48 redstones, 24 laines noires, 16 vitres, 8 pistons, 8 diamants, 8 missiles FK et une télécommande. Assembler la caisse orange marquée FK-2000, la poser dans un espace dégagé puis l'ouvrir avec le pied-de-biche SBW. Le camion contient alors ses missiles et appartient au joueur qui ouvre la caisse. Les recettes du missile et de la télécommande utilisent la table de fabrication Minecraft.

Commandes client :

| Commande | Action |
| --- | --- |
| Touches de déplacement Minecraft ; touches directionnelles dans l'interface | Avancer, reculer, tourner. |
| Espace | Freiner. |
| `G`, configurable dans les contrôles Minecraft | Ouvrir l'interface tactique ; revenir de la caméra à l'interface. |
| `V` / `I` / `R`, configurables | Demander le lancement / changer l'état du moteur / changer l'état du radar, à bord ou en caméra distante. |
| Boutons de l'interface | Moteur, radar, acquisition automatique, tir automatique, rechargement, lancement et caméra. |
| Clic sur un contact | Demander son verrouillage. |
| Échap | Quitter le contrôle distant et restaurer la vue normale. |

La télécommande fonctionne dans la même dimension sans limite de distance par défaut, avec son propriétaire et son association vérifiés côté serveur. Le camion possède des tickets de chunks persistants et le missile des tickets temporaires libérés à la fin du vol. Les cibles déjà sélectionnées restent actives pendant la préparation. Le radar cherche les entités chargées ; il ne découvre pas un avion inconnu dans une région entièrement déchargée. La caméra embarquée distante nécessite aussi que le client suive l'entité : le chargement serveur ne téléporte pas le joueur et ne remplace pas sa distance d'affichage.

Le tir automatique est désactivé par défaut. Le mode aérien conserve la sélection des véhicules volants, distincte du mode toutes les entités.

Captures réelles dans Minecraft avec SBW : [véhicule provisoire](docs/images/vehicle-prototype.png), [radar et cible AH-6 verrouillée](docs/images/tactical-radar.png), [caméra embarquée](docs/images/onboard-camera.png).

## Architecture

Le code Java se trouve dans `src/main/java/fr/irux/fk2000/` :

| Package | Responsabilité |
| --- | --- |
| `config` | Paramètres Forge : conduite, portées, lancement et dégâts. |
| `entity.vehicle` | Véhicule, conducteur, propriété et cycle du lanceur. |
| `entity.missile` | Entité missile et transitions synchronisées. |
| `physics` | Calculs de mouvement et de guidage. |
| `radar` | Détection périodique, contacts et validation des cibles. |
| `compat` | Pont SBW et extension à d'autres véhicules aériens. |
| `item`, `network` | Déploiement, association, validation serveur et échanges client/serveur. |
| `client` | Interface tactique, caméra et rendu provisoire original. |

Le lanceur prépare progressivement le tir ; le missile doit ensuite suivre les phases **EJECTING → ORIENTING → BOOSTING → GUIDING → DETONATED**. La gravité, l'accélération, la rotation, les collisions et les dégâts relèvent du serveur. Les clients affichent les états synchronisés, le radar, les animations et les effets.

Les options sont définies par la configuration Forge serveur `iruxfk2000-server.toml`, générée dans `serverconfig/` du monde. Forge transmet cette configuration aux clients connectés. Les commentaires du fichier généré indiquent les unités ; un tick vaut 1/20 de seconde.

| Paramètre | Valeur initiale |
| --- | --- |
| Vitesse véhicule / accélération / freinage | 0,45 bloc/tick ; 0,012 et 0,035 bloc/tick². |
| Télécommande / portée d'engagement initiale | Même dimension sans limite de distance / 160 blocs, réglable jusqu'à 2 048. |
| Destination manuelle X/Y/Z | Jusqu'à 4 096 blocs du camion. |
| Chunks camion / couloir missile | 3 × 3 autour du camion, plus contacts sélectionnés ; couloir mobile borné devant le missile. |
| Budgets par dimension | 16 camions, 32 missiles, 1 024 tickets de chunks. |
| Acquisition automatique / tir automatique | Activée / désactivé. |
| Capacité / préparation / rechargement individuel | 8 missiles ; 40 / 16 ticks. |
| Intervalle de rafale automatique | 30 ticks après la préparation initiale. |
| Éjection / orientation | 1,2 bloc/tick vertical ; 20 ticks. |
| Accélération / vitesse missile / rotation maximale | 0,08 bloc/tick² ; 2,5 blocs/tick ; 5°/tick. |
| Durée de vol guidé / coordonnées / fusée de proximité | 2 400 / 3 600 ticks (2 / 3 minutes) ; 2 blocs. |
| Dégâts de l'ogive / puissance explosion / dégâts aux blocs | 160 avant blindage ; 4 / désactivés. |
| Intensité des propulseurs latéraux | 2 particules, ajustable de 0 à 8. |

Les clés `chunkLoading.*`, `radar.maxEngagementRange`, `radar.coordinateTargetRange`, `vehicle.tubeCapMode`, `missile.guidedLifetimeTicks` et `missile.coordinateLifetimeTicks` règlent ces ajouts. `guidedLifetimeTicks` remplace l'ancien `lifetimeTicks` pour permettre les vols plus longs. Les tickets de camions sont restaurés au redémarrage ; les missiles en vol sont arrêtés à l'extinction du serveur. Les plafonds configurés limitent le chargement et ne sont pas une promesse de ressources illimitées.

Le fichier client `iruxfk2000-client.toml` règle la fumée, le volume du moteur, l'intensité et l'activation du zoom/tremblement de l'onde de choc. Le son de vol est une boucle mono courte attachée au missile, atténuée avec la distance et arrêtée par un fondu de 0,4 seconde après sa disparition. Les effets utilisent les passes standard de Minecraft ; le bloom et l'éclairage de la scène dépendent du shader choisi. Les packs de shaders et leur chargeur restent des installations séparées du JAR de l'addon.

## Validation

Commandes de validation du noyau :

```bash
./gradlew test
./gradlew -PrunDirectory=run/core-gametest runGameTestServer
```

Le script autonome `bash scripts/test-physics.sh` permet également de reproduire les 16 tests mathématiques avec Java 17 et JUnit épinglé : guidage, réaction des jets, onde de choc et affichage radar. Il ne démarre pas Minecraft et ne remplace pas les GameTests.

Les scénarios serveur validés couvrent une conduite réelle avec collision et expiration des commandes, la persistance sûre du propriétaire, le refus des commandes non autorisées, la consommation d'un seul missile au rechargement, le radar avec exclusion d'un allié, le lancement préparé sans consommation multiple, l'éjection moteur éteint et la rotation progressive avant allumage, l'impact avec terrain préservé, la perte de cible avec délai, la durée de vol restaurée et l'interception avec dégâts serveur. Les échecs initiaux de fixtures ont été diagnostiqués puis les scénarios ont été relancés avec leurs assertions conservées.

Les essais graphiques ont vérifié l'association, la télémétrie, la conduite distante, la sélection d'un AH-6 de SBW, le lancement avec consommation d'une munition, la caméra et le retour au joueur. Ils utilisent un client et son serveur intégré. Une session à deux clients réels, les reconnexions sur serveur public et la charge avec de nombreux véhicules ne sont pas encore validées. La matrice serveur SBW `0.8.9-final`, `0.8.9.1-hotfix` et `0.8.9.2` est suivie séparément dans le document de compatibilité.

Le châssis utilise huit sections de collision courtes qui suivent son orientation. Les coins vides de sa boîte de rendu ne bloquent plus le déplacement. Le franchissement accepte des marches de deux blocs et demande une pente praticable. Le guidage vérifie le terrain chargé, avec une marge de montée ; une paroi trop proche, un plafond ou une cible hors des chunks chargés peuvent empêcher l'interception. Le véhicule autonome conserve sa santé et son blindage propres ; les outils de réparation SBW ne sont pas intégrés.

Les modèles importés et les anciens modèles de remplacement ont des licences distinctes ; voir IMPORTED-ASSETS.md. Aucune ressource propriétaire de SBW n'est copiée. Le code et les ressources originaux de l'addon sont sous licence MIT ; les modèles et textures importés de CGTrader ne sont pas sous licence MIT ; `META-INF/LICENSE` est inclus dans le JAR construit. Cette licence ne remplace pas celles des dépendances séparées ni celle des scripts et du JAR Gradle Wrapper, dont les en-têtes et notices officiels sont conservés.
