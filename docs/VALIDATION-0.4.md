# Validation 0.4.0 — contrôle distant, radar et effets

Minecraft Java 1.20.1, Forge 47.4.10, Java 17. Validation du 25 octobre 2026.

## Comportement

- **Chunks et télécommande** : les camions possédés conservent des tickets Forge persistants sur leur zone et les contacts déjà sélectionnés. Les vols utilisent des tickets temporaires devant le missile et autour d'une cible connue. Les tickets de vol sont libérés à l'impact, à l'expiration et à l'arrêt du serveur. Le contrôle distant reste réservé au propriétaire, à sa télécommande associée et à la même dimension ; sa limite de distance est désactivée par défaut. Les tickets serveur ne remplacent pas le suivi graphique du véhicule par le client.
- **Ciblage** : aérien, tous les véhicules SBW, entités vivantes/véhicules ou coordonnées X/Y/Z. Les règles alliées s'appliquent dans tous ces modes. Les coordonnées ne créent aucune fausse entité et sont bornées côté serveur. La portée d'engagement est réglable par véhicule de 16 à 2 048 blocs par défaut ; une destination fixe peut se situer à 4 096 blocs.
- **Répartition automatique** : salve sur le contact choisi, avec arrêt après sa perte, ou un missile par contact marqué sur le radar. Les cibles ne sont retirées de la file qu'après l'ajout réel du missile et la consommation d'une munition. Changer de répartition annule la préparation en cours.
- **Radar** : carte de terrain 64 × 64, zoom à la molette ou +/−, balayage de six secondes et luminosité des échos bornée à 40 % entre les passages. Le zoom est distinct de la portée de tir. Le serveur échantillonne les chunks résidents sans attendre leur génération ; les régions inconnues restent sombres. Une texture de carte est libérée lorsque l'écran est remplacé ou fermé.
- **Bouchons et suspension** : éjection d'un bouchon physique, ouverture latérale avec fermeture, ou tubes ouverts. Chaque lancement comprime brièvement la suspension arrière ; les roues restent au sol pendant l'animation du châssis. Les tubes réutilisés peuvent recevoir un nouveau bouchon avec leur munition.
- **Destruction** : une seule explosion et une carcasse noire sauvegardée, éjection sûre du conducteur, protection des alliés et terrain préservé par défaut. Le propriétaire peut retirer sa carcasse en s'accroupissant avec le pied-de-biche SBW.
- **Missile et effets** : plume émissive fixée à la tuyère arrière, fumée originale, buses de correction sur quatre côtés commandées par la réaction opposée à la rotation, anneau de choc et impulsion de caméra/FOV configurable. Les sprites de feu détachés de l'ancienne traînée sont supprimés.
- **Son** : allumage de 0,42 s, boucle de vol mono de 0,64 s suivant une source par missile, atténuation spatiale et léger changement de hauteur avec la vitesse radiale. La boucle monte en trois ticks et s'éteint en huit ticks après disparition du missile. L'explosion est un son séparé de 1,05 s. Aucun enregistrement ou son SBW n'est incorporé.

## Tests serveur et mathématiques

Les scénarios comprennent les fonctions 0.3 et les nouveaux mécanismes : tickets Forge réels, restauration des données d'ancrage, refus des tickets orphelins, déconnexion du propriétaire, contrôle à 384 blocs, tir à distance et maintien d'une cible après déplacement du propriétaire à 1 024 blocs, cibles au-dessus de la hauteur de construction, portée réglable, répartition sur deux contacts, coordonnées et vol réel de 650 blocs dépassant l'ancienne durée de 240 ticks.

| Distribution | Résultat | Preuve |
| --- | --- | --- |
| SBW 0.8.9.2 officiel | 46/46, exit 0 | [journal](test-results/upgrade-0.4/sbw-0.8.9.2.log) |
| JAR utilisateur SBW 0.8.9 — 95d7cfe4e | 46/46, exit 0 | [journal](test-results/upgrade-0.4/sbw-uploaded95.log) |
| Noyau Forge sans SBW | 39/39, exit 0 | [build et journal](test-results/upgrade-0.4/core-build.log) |
| Guidage, jets, choc et affichage radar | 16/16 JUnit | [rapport XML](test-results/upgrade-0.4/guidance-junit.xml) |

Cela représente **131 exécutions GameTest et 16 tests JUnit réussis**. Le JAR final réobfusqué contient 103 classes Java 17 ; son empreinte est publiée dans [SHA256SUMS.txt](../downloads/SHA256SUMS.txt).

Les tests de carte vérifient les couleurs réelles de blocs et l'absence d'augmentation du nombre de chunks chargés lors d'un aperçu de 4 096 blocs de large. Les tests de répartition comptent les UUID des missiles effectivement créés et leurs cibles enregistrées. Les fixtures aériennes distantes attendent leur première observation radar et sont placées en ciel ouvert, plutôt que dans la roche naturelle hors de la structure de test.

## Rendu et shaders

Le profil de développement `-PshaderTest=true` ajoute **Oculus 1.8.0** et **Embeddium 0.3.31** au client de test. Ces dépendances et les shaderpacks restent séparés de l'addon. Les fichiers officiels ont été téléchargés avec vérification SHA-512 : [versions, sources et licences](SHADER-TEST-ARTIFACTS.json).

**Complementary Reimagined r5.9.3** : client réel, monde normal, cabine, réglages, carte/zoom/balayage, sélection de deux AH-6, lancement automatique, explosion et carcasse noire affichés avec le shader actif. Le client s'est fermé normalement, exit 0. Le profil POTATO a été utilisé pour le rendu logiciel Mesa/llvmpipe ; les réglages graphiques élevés ne sont pas qualifiés par cet essai.

**Hysteria Shaders Universal 1.2.1** : client réel avec le shader actif dans l'Overworld, réglages du ciblage, coordonnées validées par le serveur, point sur la carte et lancement réel avec plume et fumée. Profil `lowest`, TAAU à 0,5, Mesa/llvmpipe ; fermeture normale, exit 0. Les deux clients utilisaient le JAR SBW fourni par l'utilisateur. Journaux : [Complementary](test-results/upgrade-0.4/client-complementary.log), [Hysteria](test-results/upgrade-0.4/client-hysteria.log).

Les [captures réelles](images/v04/README.md) et leur [ZIP en qualité originale](../downloads/FK-2000-images-0.4.zip) accompagnent cette version. Elles montrent les profils de test indiqués, sans retouche ni agrandissement.

Les effets utilisent les passes standard d'entités et de particules, et les événements Forge de caméra/FOV. Ils ne remplacent pas le framebuffer ni le programme de post-traitement du shaderpack. Le bloom, les couleurs et l'éclairage de la scène dépendent du pack et de ses réglages ; une plume émissive n'est pas une garantie d'éclairage dynamique du terrain avec tous les packs.

Le périphérique sonore virtuel OpenAL « No Output » valide le chargement des ressources et l'exécution des sources, pas une écoute sur haut-parleurs. Les fichiers Vorbis sont mono, à 44 100 Hz, et la génération procédurale des ressources est reproductible.

## Configuration et limites

Par dimension, les plafonds initiaux sont de 16 camions, 32 missiles et 1 024 tickets de chunks. La zone initiale du camion fait 3 × 3 chunks ; ses contacts déjà sélectionnés peuvent ajouter des tickets, dans le même budget. Le radar n'invente pas les entités d'une région entièrement déchargée et inconnue. Les camions sont restaurés au redémarrage ; les vols sont interrompus et les sélections réinitialisées à l'arrêt du serveur.

`missile.guidedLifetimeTicks` remplace l'ancien `lifetimeTicks` et vaut 2 400 ticks ; `coordinateLifetimeTicks` vaut 3 600 ticks. Les bornes de coordonnées, de portée et de tickets restent administrables dans la configuration serveur. La durée de vie, la vitesse d'une cible et le relief peuvent limiter une interception même lorsqu'une destination est autorisée.

Le fichier client règle le volume du moteur, la fumée et l'activation/intensité du choc de caméra. Le protocole de contrôle passe à **3** : remplacer le JAR sur le client et le serveur. Aucun essai à deux clients réels, test de charge de nombreux lanceurs ni certification de tous les packs, pilotes, dimensions ou préréglages graphiques n'est revendiqué.

Les modèles CGTrader conservent leur licence distincte. Le code, les nouvelles textures et les sons de l'addon sont originaux ; aucune ressource propriétaire de SBW ou de shaderpack n'est copiée dans le livrable. Voir [la provenance des modèles](IMPORTED-ASSETS.md).
