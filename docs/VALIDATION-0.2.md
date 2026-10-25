# Validation 0.2.0 — graphisme importé

Minecraft 1.20.1, Forge 47.4.10, Java 17. Validation réelle du 25 octobre 2026.

## Modèles et matériaux

- Camion fourni : 65 objets, 719 331 faces avant conversion ; maillage de jeu de 18 368 triangles, huit roues articulées, un châssis et un lanceur mobile à six tubes.
- Atlas diffuse original de camouflage : 2048 × 2048, avec UV calculés, métal, vitres et pneus différenciés. Texture de radar originale avec grain et détails de panneau.
- Missile fourni : 418 triangles, axe normalisé pour le guidage et texture diffuse UV fournie. Les faces superposées sont rendues avec orientation cohérente et élimination des faces arrière pour éviter le scintillement des ailerons.
- Points de sortie des missiles déplacés vers les six tubes du nouveau lanceur ; contrôles, dégâts et guidage restent décidés par le serveur.
- Les géométries sont converties par Blender, sans entraînement ni génération d'images par IA. Les licences des ressources CGTrader restent distinctes de la licence MIT du code : [provenance](IMPORTED-ASSETS.md).

## Tests réellement exécutés

La suite mathématique compte 10 tests réussis, aucun échec/ignoré. Les 13 GameTests serveur ont été exécutés séparément avec chacune des quatre distributions suivantes, soit 52 exécutions réussies :

| SBW | Résultat | Preuve |
| --- | --- | --- |
| 0.8.9-final — LAITrju0 | 13/13, exit 0 | [journal](test-results/visual-0.2/sbw-final.log) |
| 0.8.9.1-hotfix — mHmtMuKF | 13/13, exit 0 | [journal](test-results/visual-0.2/sbw-hotfix.log) |
| 0.8.9.2 — 81Iyv9ao | 13/13, exit 0 | [journal](test-results/visual-0.2/sbw-0.8.9.2.log) |
| JAR utilisateur 0.8.9 — 95d7cfe4e | 13/13, exit 0 | [journal](test-results/visual-0.2/sbw-uploaded95.log) |

SHA-256 du JAR SBW fourni : `3c7d6c13b903dc337d8eecab47ef9ce84c7aa2b30c313d73b6fca2569ee67d0a`. Ce JAR n'est pas redistribué avec le mod.

Pour reproduire le dernier cas avec le fichier possédé localement :

```bash
./gradlew --no-daemon -PsbwVersion=0.8.9-final -PsbwLocalJar=/chemin/superbwarfare-0.8.9-mc1.20.1-95d7cfe4e-all.jar -PrunDirectory=run/sbw-uploaded build runGameTestServer
```

Le profil final fournit les dépendances de l'ancienne distribution ; le fichier local remplace seulement SBW dans le classpath et est remappé dans un cache séparé, sans modifier l'original.

## Essais graphiques réels

Client avec SBW 0.8.9-final, serveur intégré, rendu logiciel Xvfb/Mesa : apparition du camion importé et du missile texturé, acquisition d'un AH-6 SBW, préparation puis déploiement vertical des six tubes, éjection physique du missile, munitions 7 → 6, conduite distante avec télémétrie à 13 km/h, freinage, caméra et retour au joueur. Le client final s'est fermé normalement, exit 0.

Captures natives sans retouche : [camion](images/v02/truck-0.2.png), [missile posé pour le gros plan](images/v02/missile-0.2.png), [déploiement](images/v02/launcher-deploying.png), [éjection](images/v02/missile-launch.png), [radar](images/v02/radar-0.2.png).

Les tests de serveur couvrent propriété, persistance, rechargement sans duplication, commandes expirées, collision, IFF, radar, lancement, phases et interceptions fixes/mobiles. Le détail des scénarios reste dans le bilan 0.1 ; cette version les a réellement relancés avec les nouveaux points de sortie.

## Limites

Aucun essai à deux clients réels ni test de charge de nombreux véhicules n'est revendiqué. La collision du châssis conserve l'AABB vanilla de trois blocs, plus courte que ses débords visuels. Les sons existants Minecraft sont conservés. Les matériaux du jeu utilisent une texture diffuse et les normales géométriques, sans revendiquer un rendu PBR.

Les modèles de remplacement originaux restent disponibles si les ressources CGTrader ne sont pas présentes dans une compilation des sources. Les fichiers OBJ/FBX d'origine et les exports de modèles réutilisables ne sont pas mis dans le dépôt public ; le JAR de jeu contient les données nécessaires à son rendu, avec la notice de licence des ressources importées.
