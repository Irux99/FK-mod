# Validation 0.3.0 — conduite et intégration SBW

Minecraft 1.20.1, Forge 47.4.10, Java 17. Mise à jour du 25 octobre 2026.

## Changements

- Camion agrandi uniformément ×2 : châssis de 12 × 3,7 × 4,4 blocs, collisions rectangulaires englobantes suivant son orientation, contrôle des virages contre les obstacles. Roues, radar et points de sortie des six tubes utilisent la même échelle.
- Conducteur installé à l'avant, yeux dans la cabine, regard libre suivant les virages. Les triangles opaques du pare-brise sont écartés uniquement en vue intérieure. La caméra distante reste indépendante des coordonnées du joueur.
- Coque de 240 PV et blindage propres au véhicule, santé sauvegardée, barre dans le HUD conducteur et l'écran tactique, avertissement critique et indicateur d'impact.
- Franchissement de marches de terrain de deux blocs par la physique Minecraft ; les murs et falaises plus hauts restent bloquants.
- Missile : recherche du relief dans les chunks déjà chargés, montée anticipée, maintien au-dessus d'une crête et retour vers une cible plus basse. Réduction de vitesse en virage serré, accélération lorsqu'il est aligné, poursuite d'une cible rapide.
- Ogive réglable à 160 dégâts avant blindage. Les dégâts utilisent le type d'explosion SBW lorsqu'il existe, avec filtrage allié et couverture ; la destruction des blocs reste désactivée par défaut. Le souffle vanilla ne double pas les dégâts.
- Rechargement par clic droit accroupi avec un missile : délai de 16 ticks (0,8 s), son mécanique original de 0,62 s et sous-titres. En mode automatique, tirs espacés de 30 ticks après la préparation initiale, tubes maintenus verticaux entre les tirs puis rabaissement à vide, sans cible ou après désactivation.
- Vraie recette SBW, onglet FK ajouté à sa table, caisse orange SBW avec logo original de l'addon, animation et déploiement au pied-de-biche SBW. L'espace nécessaire au châssis est vérifié avant l'ouverture ; le propriétaire, les missiles et le radar sont initialisés dans la caisse. Recettes Minecraft pour les missiles et la télécommande.

## Tests

Les 10 tests JUnit du guidage mathématique passent. La suite serveur comporte désormais 24 GameTests avec SBW : 19 tests du noyau et 5 tests utilisant les classes et objets de la distribution SBW installée.

Elle vérifie notamment le franchissement réel d'une marche de deux blocs et le refus d'un mur de cinq blocs, le siège et les collisions après rotation, les dégâts/persistance des PV, la recharge accroupie sans duplication, deux tirs automatiques espacés et le rabaissement, une crête de 44 blocs devant une cible plus basse, les cibles fixes/mobiles/basses/rapides, l'IFF et le craft/déploiement natifs SBW.

| Exécution finale | Résultat | Preuve |
| --- | --- | --- |
| SBW 0.8.9.2 officiel | 24/24, exit 0 | [journal](test-results/upgrade-0.3/sbw-0.8.9.2.log) |
| JAR SBW fourni 0.8.9 — 95d7cfe4e | 24/24, exit 0 | [journal](test-results/upgrade-0.3/sbw-uploaded95.log) |
| Forge sans SBW | 19/19, build final et exit 0 | [journal](test-results/upgrade-0.3/core-release-build.log) |
| JUnit mathématique | 10/10 | [rapport XML](test-results/upgrade-0.3/guidance-junit.xml) |

Soit 67 exécutions GameTest réussies. Le hotfix 0.8.9.1 et la distribution Modrinth 0.8.9-final ont été inspectés et testés pour les versions antérieures ; la suite 0.3 complète n'est pas revendiquée sur ces deux fichiers. Le JAR utilisateur, testé ici, est une distribution distincte.

Le test SBW 0.8.9.2 mesure un AH-6 à **250 → 139,4 PV**, soit **110,6 PV retirés** après ses modificateurs de dégâts. Un AH-6 voisin appartenant au joueur reste à **250 PV**. Ce résultat dépend du blindage du véhicule, de sa distance et de la couverture ; il ne promet pas une valeur identique sur tous les véhicules.

## Client graphique

Client réel avec le JAR SBW fourni, serveur intégré et Xvfb/Mesa : camion agrandi, position du conducteur à l'avant avec vue extérieure dégagée, barre de coque affichant les dégâts, télémétrie de santé dans l'écran tactique sans HUD dupliqué, onglet FK avec aperçu du véhicule et matériaux, pose de la caisse marquée et déploiement réel au pied-de-biche. Le client final s'est fermé normalement, exit 0. Les images sont des captures natives, sans retouche : [camion](images/v03/truck-0.3.png), [cabine](images/v03/driver-cab-0.3.png), [santé tactique](images/v03/tactical-health-0.3.png), [assemblage](images/v03/assembly-table-0.3.png), [caisse](images/v03/orange-crate-0.3.png).

Le moteur sonore OpenAL a démarré avec son périphérique virtuel « No Output » et a chargé/joué l'événement `iruxfk2000:vehicle_reload` sans erreur de ressource. Le fichier Vorbis original est valide et reproductible ; aucun essai d'écoute sur haut-parleurs n'est revendiqué dans cet environnement sans sortie physique. [Journal client final](test-results/upgrade-0.3/client-visual.log).

## Configuration et limites

Les nouvelles clés `vehicle.quickReloadTicks`, `vehicle.autoFireIntervalTicks` et `missile.warheadDamage` s'appliquent aux mondes existants. L'ancienne clé `reloadTicks` est remplacée. Les autres réglages existants sont conservés. Le protocole réseau passe à `2` : installer le même JAR sur le client et le serveur, en retirant les anciennes versions du dossier `mods/`.

La collision reste une AABB englobante, donc ses coins sont conservateurs en diagonale. Le camion gravit les pentes praticables, pas les parois verticales. Le missile ne charge pas de chunks et ne garantit pas une interception si l'obstacle est trop proche ou si la cible quitte le terrain chargé. Le FK-2000 reste une entité autonome ; les outils de réparation SBW ne sont pas intégrés. Aucun essai de charge ni session à deux clients réels n'est revendiqué.

Les sons de rechargement, le logo et le code sont originaux, sous licence du projet. Les modèles et textures importés conservent leur licence CGTrader distincte ; aucune ressource SBW n'est copiée dans le JAR de l'addon. Voir [la provenance](IMPORTED-ASSETS.md).
