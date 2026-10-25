# Préversion 0.5.0-preview1 — collisions et cartographie

Le camion utilise huit sections de collision courtes qui tournent avec le châssis. La grande boîte diagonale englobante sert seulement au rendu et aux vérifications des chunks : ses coins vides ne font plus partie de la collision. Les sections partagent les interactions et la santé du camion. La conduite vérifie les sections, y compris pour franchir une marche et refuser une falaise. Les missiles reconnaissent le parent de chaque section pour protéger le lanceur et ses alliés, et appliquent leur ogive une seule fois par véhicule.

La carte passe de 64 × 64 à **128 × 128**, avec couleurs des biomes, arbres, ombrage des pentes et profondeur de l'eau. Un atlas par dimension conserve les colonnes observées entre les zooms et les redémarrages, dans une limite de 2 048 chunks. Le dézoom échantillonne aussi la surface couverte par chaque pixel pour conserver les petites zones connues ; le changement de zoom garde l'ancienne carte à l'échelle en attendant la réponse serveur. Cette carte originale s'inspire de la lisibilité des minimaps : elle n'intègre ni le code ni les textures de Xaero.

En mode **Coordonnées**, cliquer dans le disque radar sélectionne X/Z et la hauteur du sol mémorisée par le serveur. La portée, le propriétaire et la session restent vérifiés. Un terrain inconnu est refusé ; les champs X/Y/Z restent disponibles pour fixer une autre altitude. Le clic sélectionne une destination ; utiliser ensuite la commande de tir.

Trois nouvelles icônes originales de 64 × 64 représentent la télécommande, le missile et le camion. La caisse orange SBW et son logo sont inchangés. Le script `scripts/assets/create_item_icons.py` reproduit ces icônes.

Le protocole principal passe à **4**, celui de la carte à **2** : remplacer le JAR sur le client et le serveur. Les modèles importés gardent leur licence séparée ; leurs fichiers source privés restent exclus du dépôt public.

La compatibilité spécifique avec **Xenon** est reportée : aucun contournement d'un mécanisme de gel de ce mod n'est revendiqué. Les tickets serveur de FK-2000 restent actifs ; ils ne contrôlent pas les décisions d'affichage ou de suspension d'un autre mod.

## Mods de performances

Le profil de développement optionnel `-PperformanceTest=true` charge Create 6.0.8, CreateBetterFps 1.1.2, Embeddium 0.3.31, Oculus 1.8.0 et Entity Culling 1.11.3 pour Forge 1.20.1. Ces mods restent séparés du JAR distribué.

L'intégration Entity Culling ajoute uniquement le camion, le missile et le bouchon de FK-2000 aux listes publiques `entityWhitelist` et `tickCullWhitelists`. Elle ne désactive pas l'optimisation des autres entités et ne modifie pas le fichier de configuration de l'utilisateur. Si l'API d'une future version diffère, un avertissement est journalisé. Un secours dans la réception des transformations applique la position serveur si les ticks visuels ont été suspendus pendant plus de deux ticks ; la physique reste exclusivement côté serveur.

CreateBetterFps 1.1.2 modifie les buffers de rendu de Create ; ses trois mixins ont été inspectés. Cette inspection seule ne prouve pas l'absence de conflit dans un modpack complet. Les versions officielles de Create, CreateBetterFps et Entity Culling utilisées pour les essais ont été téléchargées avec vérification de leur SHA-512 Modrinth.

## État de cette préversion

Fournie à la demande de l'utilisateur avant la fin des essais. Les régressions de collision contre ses propres sections, de placement de l'objet et de déploiement SBW ont été corrigées après les premiers essais. Le test de montagne est déplacé en ciel ouvert : la trajectoire dépassait l'arène souterraine et heurtait son plafond naturel au lieu de tester uniquement la crête artificielle.

Les essais graphiques réunissant CreateBetterFps, Create, Embeddium, Oculus et Entity Culling ne sont **pas encore exécutés**. L'API publique d'Entity Culling 1.11.3 et les artefacts ont été inspectés ; ce n'est pas une certification du modpack. Les versions et empreintes sont dans PERFORMANCE-TEST-ARTIFACTS.json. La validation SBW complète de cette préversion reste à terminer ; les chiffres de la version 0.4 ne valident pas automatiquement la 0.5.

Le radar affiche jusqu'à 32 missiles actifs provenant du camion contrôlé, grâce aux positions et vitesses serveur. Les flèches bleues indiquent le sens du vol ; hors de la vue, elles restent sur le bord du radar. Le survol indique la distance et l'altitude. La portée d'affichage de la carte ne change pas la portée du missile.

## Résultat vérifié de la préversion

Build réobfusqué et **39/39 GameTests du noyau Forge sans SBW réussis**, exit 0. Cela couvre notamment le mouvement, le franchissement, la collision, le placement, la carte et sa persistance, le dézoom maximal et le vol au-dessus de la crête en ciel ouvert. [Journal](test-results/upgrade-0.5/preview-core.log). Le JAR contient 110 classes Java 17, les ressources de rendu importées et aucune classe des mods de performances. Les essais d'intégration SBW complets et le client graphique avec le pack de performances restent à exécuter.
