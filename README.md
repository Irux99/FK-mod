# Irux FK-2000

Addon Minecraft Java **1.20.1**, **Forge 47.4.10**, **JDK 17**, mod ID `iruxfk2000`. Le mod ajoute un véhicule antiaérien original avec conduite locale et à distance, radar tactique et missiles à lancement vertical à froid.

**Version 0.1.0 : mécaniques fonctionnelles, modèles artistiques provisoires.** La compilation réelle, la réobfuscation et les 10 tests unitaires de guidage ont réussi. Le client a été utilisé avec SBW 0.8.9-final : conduite distante, association en créatif, radar sur un AH-6 réel, verrouillage, lancement et caméra sans téléportation. Les résultats exacts des tests serveur par version sont consignés dans [le bilan de validation](docs/VALIDATION.md) et [la matrice SBW](docs/SBW-COMPATIBILITY.md). Les modèles originaux en volumes simples servent au jeu et aux essais ; ils ne constituent pas une finition artistique définitive.

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
- Pour recharger, rester à moins de huit blocs du véhicule, disposer de l'objet missile et utiliser le bouton de rechargement ; le délai et la capacité se règlent dans la configuration.

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

La caméra distante dépend du suivi client de l'entité. Si le véhicule reste accessible côté serveur mais sort de la distance de suivi client, l'interface peut afficher des informations radar sans permettre la vue embarquée. Le mod ne doit ni téléporter le joueur ni forcer le chargement de chunks pour maintenir la caméra.

Le tir automatique est désactivé par défaut. Garder les essais initiaux en tir manuel et vérifier les règles alliées avant toute activation. Le radar travaille sur les entités déjà chargées et ne détecte pas les joueurs comme cibles aériennes.

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
| Portée télécommande / radar | 192 / 160 blocs, sans chargement forcé. |
| Acquisition automatique / tir automatique | Activée / désactivé. |
| Capacité / préparation / rechargement individuel | 8 missiles ; 40 / 60 ticks. |
| Éjection / orientation | 1,2 bloc/tick vertical ; 20 ticks. |
| Accélération / vitesse missile / rotation maximale | 0,08 bloc/tick² ; 2,5 blocs/tick ; 5°/tick. |
| Durée de vol / fusée de proximité | 240 ticks / 2 blocs. |
| Puissance explosion / dégâts aux blocs | 3 / désactivés. |
| Intensité des propulseurs latéraux | 2 particules, ajustable de 0 à 8. |

 Ne pas modifier le code pour régler les portées, vitesses, durées, munitions ou dégâts. Ne pas activer la destruction de blocs sur un monde important avant validation sur une copie.

## Validation

Commandes de validation du noyau :

```bash
./gradlew test
./gradlew -PrunDirectory=run/core-gametest runGameTestServer
```

Le script autonome `bash scripts/test-physics.sh` permet également de reproduire les 10 tests mathématiques avec Java 17 et JUnit épinglé. Il ne démarre pas Minecraft et ne remplace pas les GameTests.

Les scénarios serveur validés couvrent une conduite réelle avec collision et expiration des commandes, la persistance sûre du propriétaire, le refus des commandes non autorisées, la consommation d'un seul missile au rechargement, le radar avec exclusion d'un allié, le lancement préparé sans consommation multiple, l'éjection moteur éteint et la rotation progressive avant allumage, l'impact avec terrain préservé, la perte de cible avec délai, la durée de vol restaurée et l'interception avec dégâts serveur. Les échecs initiaux de fixtures ont été diagnostiqués puis les scénarios ont été relancés avec leurs assertions conservées.

Les essais graphiques ont vérifié l'association, la télémétrie, la conduite distante, la sélection d'un AH-6 de SBW, le lancement avec consommation d'une munition, la caméra et le retour au joueur. Ils utilisent un client et son serveur intégré. Une session à deux clients réels, les reconnexions sur serveur public et la charge avec de nombreux véhicules ne sont pas encore validées. La matrice serveur SBW `0.8.9-final`, `0.8.9.1-hotfix` et `0.8.9.2` est suivie séparément dans le document de compatibilité.

Limites de cette version : modèle 3D provisoire, sons Minecraft existants, collision vanilla carrée de trois blocs malgré le porte-à-faux visuel du châssis, et absence de recettes de fabrication en survie. Les objets sont disponibles dans l'onglet créatif ou par commande. Le radar et l'IFF intègrent SBW, mais les systèmes propres à SBW de réparation, de blindage et de munitions ne sont pas réutilisés par le véhicule autonome.

Les formes 3D provisoires, l'ergonomie et les effets nécessitent une revue visuelle en jeu. Aucune ressource propriétaire de SBW n'est copiée. Le code et les ressources originaux de l'addon sont sous licence MIT ; `META-INF/LICENSE` est inclus dans le JAR construit. Cette licence ne remplace pas celles des dépendances séparées ni celle des scripts et du JAR Gradle Wrapper, dont les en-têtes et notices officiels sont conservés.
