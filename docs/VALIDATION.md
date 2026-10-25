# Bilan de validation — Irux FK-2000 0.1.0

Validation du **25 octobre 2026**, sur Minecraft **1.20.1**, Forge **47.4.10**, Temurin **JDK 17.0.17**, Gradle **8.8** et ForgeGradle **6.0.36**. Les commandes ont été exécutées réellement dans l'environnement cloud ; les résultats ci-dessous distinguent tests serveur, client graphique et contrôle du JAR de production.

## Compilation et calculs de guidage

Le build Gradle, la compilation Java 17 et la réobfuscation du JAR réussissent. La suite `GuidanceMathTest` contient **10 tests : 10 réussis, 0 échec, 0 erreur, 0 ignoré**. Elle couvre notamment la rotation bornée, les directions opposées ou nulles, l'interception d'une cible mobile et la distance balayée de la fusée de proximité.

Rapport du runner conservé : [guidance-junit.xml](test-results/guidance-junit.xml). Le fichier généré par Gradle est `build/test-results/test/TEST-fr.irux.fk2000.physics.GuidanceMathTest.xml`. Le [journal du build final](test-results/release-build.log) accompagne les sources. Le runner autonome `bash scripts/test-physics.sh` a également exécuté les dix tests avec le vrai compilateur Java 17 et JUnit, sans remplacer les classes Minecraft par des simulations.

## Matrice serveur avec les distributions officielles SBW

Les mêmes **13 GameTests requis** ont été exécutés dans trois instances Forge de test distinctes. Chaque dernière exécution ci-dessous contient bien `13 GAME TESTS COMPLETE`, `All 13 required tests passed` et `BUILD SUCCESSFUL`, avec un code de sortie **0** : **39 exécutions réussies au total**, sans scénario ignoré ou échec attendu.

| Distribution officielle | Résultat | Journal final conservé |
| --- | --- | --- |
| SBW `0.8.9-final`, version Modrinth `LAITrju0` | **13/13 réussis**, exit 0 | [sbw-0.8.9-final.log](test-results/sbw-0.8.9-final.log) |
| SBW `0.8.9.1-hotfix`, version Modrinth `mHmtMuKF` | **13/13 réussis**, exit 0 | [sbw-0.8.9.1-hotfix.log](test-results/sbw-0.8.9.1-hotfix.log) |
| SBW `0.8.9.2`, version Modrinth `81Iyv9ao` | **13/13 réussis**, exit 0 | [sbw-0.8.9.2.log](test-results/sbw-0.8.9.2.log) |

Les binaires et les JAR de sources officiels ont été vérifiés par SHA-512 avant inspection ; les identifiants exacts et empreintes figurent dans [SBW-ARTIFACTS.json](SBW-ARTIFACTS.json). Les dépendances runtime sont remappées par ForgeGradle dans son cache ; les fichiers officiels originaux restent inchangés.

### Scénarios réellement exécutés

| Ensemble | Contrôles |
| --- | --- |
| Véhicule — 5 tests | Propriété et restauration NBT sûre ; rechargement consommant un seul objet et refus des répétitions/non-propriétaires ; association réelle de télécommande en créatif via `Player.interactOn` ; accélération, collision avec un mur, marche arrière puis arrêt après expiration des commandes ; radar, alliés, verrouillage, préparation et consommation unique au lancement. |
| Missile — 6 tests | Éjection verticale physique moteur éteint ; orientation progressive avant allumage ; collision solide et préservation du terrain ; perte de cible avec délai puis expiration sans explosion ; durée de vol restaurée par NBT ; interceptions réelle fixe et mobile avec dégâts calculés par le serveur. Le scénario mobile exige un déplacement supérieur à quatre blocs et le passage par `BOOSTING` puis `GUIDING`. |
| SBW — 2 tests | Construction des vrais A-10A, AH-6 et ZTZ-99A de chaque distribution ; type aérien, épave et véhicule terrestre ; prédicat IFF officiel, dernier conducteur et équipes ; vrais contacts radar, verrouillage de l'AH-6, ordre de tir, absence de doublon, missile consommé une seule fois et éjection physique avant allumage. |

Un premier scénario de cible mobile plaçait sa cible à 75 blocs au-dessus du repère d'une fixture de seulement 48 blocs de haut. Dans un monde de test neuf, la cible se trouvait dans la pierre naturelle hors du volume dégagé. La lecture des blocs sauvegardés a confirmé cet obstacle. Les deux arènes originales couvrent désormais **32 × 96 × 32 blocs**, avec air explicite et sol en pierre ; les tests vérifient que le couloir de vol est bien libre. Les assertions de mouvement, de phases et de dégâts ont été conservées et renforcées. Les trois résultats finaux du tableau utilisent cette fixture corrigée.

Le runner GameTest de cette version Forge écrit ses résultats dans les journaux. Les XML de la suite mathématique JUnit sont distincts ; aucun XML GameTest inexistant n'est revendiqué.

### Reproduire la matrice

Depuis le dossier du projet, avec Java 17 :

```bash
source scripts/cloud-env.sh  # dans l'instance cloud préparée
./gradlew --no-daemon -PsbwVersion=0.8.9-final -PrunDirectory=run/sbw-0.8.9-final build runGameTestServer
./gradlew --no-daemon -PsbwVersion=0.8.9.1-hotfix -PrunDirectory=run/sbw-0.8.9.1-hotfix build runGameTestServer
./gradlew --no-daemon -PsbwVersion=0.8.9.2 -PrunDirectory=run/sbw-0.8.9.2 build runGameTestServer
```

Utiliser ces répertoires uniquement pour des mondes d'essai GameTest. Vérifier le code de sortie et le nombre de scénarios terminés. Un build seul ou un démarrage sans scénario ne remplace pas ces contrôles.

## Client graphique réellement essayé

Le client Forge a démarré avec un affichage virtuel Xvfb et OpenGL logiciel. Un monde créatif a été ouvert et les essais suivants ont été réalisés avec **SBW 0.8.9-final** et ses dépendances officielles :

- Apparition du FK-2000 avec le rendu original provisoire.
- Association d'une télécommande neuve en créatif ; le vrai objet tenu conserve les tags UUID du véhicule et dimension. Un défaut de copie d'objet en créatif a été corrigé et possède un test de régression serveur.
- Ouverture du radar tactique, réception de la télémétrie et affichage d'un véritable AH-6 SBW créé dans le monde.
- Sélection du contact et affichage du verrouillage à environ 65 mètres ; bouton de lancement accepté et munitions passant de 8 à 7. La destruction de cet AH-6 n'est pas revendiquée par cet essai graphique.
- Activation du moteur et conduite depuis l'interface : télémétrie à 15,6 km/h et déplacement réel du véhicule.
- Caméra embarquée avec HUD, retour au radar avec `G`, fermeture avec Échap et restitution de la vue du joueur. Le joueur reste à `[0.5, -60, -4.5]` pendant le déplacement du véhicule : aucune téléportation du joueur.

Le client s'est fermé normalement, avec un build terminé par exit 0. Le journal de cette instance reste à `/workspace/client-smoke/gradle-client-final-validation.log` ; les captures conservées avec les sources sont : [véhicule provisoire](images/vehicle-prototype.png), [radar tactique](images/tactical-radar.png) et [caméra embarquée](images/onboard-camera.png).

Il s'agit d'un vrai client et de son serveur intégré. **Aucun essai avec deux clients réseau distincts n'a été exécuté.** Les vérifications de propriété et de consommation décrites plus haut sont des scénarios serveur, pas des tests de paquets malveillants ou de charge réseau.

## Contrôle séparé du JAR de production

Une installation officielle de Forge 47.4.10 hors du classpath Gradle de développement a été préparée. Une copie de contrôle stable du JAR réobfusqué, antérieure au dernier assemblage de livraison, a été contrôlée : 33 classes Java, ressources et licence présentes. Le reçu identifie exactement cette copie ; il ne doit pas être confondu avec l'empreinte du JAR final. Le bootstrap `forgeserver` avec SBW 0.8.9.2 et Kotlin for Forge trouve les dépendances et ne produit pas d'erreur de liaison dans cette phase.

Le démarrage s'arrête ensuite normalement sur le message EULA Minecraft, avec exit 0 : **aucun monde ni gameplay de production n'est déclaré validé par ce contrôle**. Aucune acceptation de l'EULA n'a été écrite. Le [compte rendu du bootstrap](test-results/production-bootstrap.txt) et le [reçu des artefacts contrôlés](test-results/production-artifact-receipt.json) sont conservés avec les sources. Les journaux complets de l'instance restent dans `/workspace/production-smoke/`.

## Limites restantes

Les modèles sont des ressources originales provisoires ; les détails artistiques, sons dédiés et finitions restent à améliorer. La collision du véhicule utilise actuellement une boîte carrée de trois blocs, plus courte que les débords du modèle. Les événements sonores Minecraft sont utilisés, sans validation d'écoute sur la machine sans périphérique audio.

La compatibilité testée concerne la classification, le radar, l'IFF et le lancement avec les trois versions SBW ci-dessus. La réparation, les munitions et le blindage propres à SBW ne sont pas intégrés au FK-2000 autonome. Les versions ultérieures, les clients graphiques hotfix/.2, les deux clients multijoueurs simultanés et les charges importantes d'entités ou de particules n'ont pas été testés. Les options automatiques sont implémentées mais la matrice décrite ici privilégie l'acquisition et le tir manuels.
