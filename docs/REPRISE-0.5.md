# Reprendre la préversion 0.5

L'utilisateur a explicitement demandé une version intermédiaire avant la validation complète.
Le dépôt est /workspace/FK-mod. Ne pas reprendre comme si tout était certifié.

Objectifs : collisions découpées, carte détaillée à la Xaero, mémoire du terrain et dézoom maximal, clic pour viser le sol, icônes améliorées sans toucher la caisse orange, suivi des missiles sur le radar, compatibilité avec Embeddium 0.3.31, CreateBetterFps et Entity Culling 1.11.3 activé.

Implémentation : huit ChassisPart Forge partagent la santé/interactions ; mouvement et marche vérifient toutes les sections, canCollideWith exclut les propres parties. Placement objet et déploiement SBW gardent la vérification de toute la longueur. TerrainAtlas SavedData mémorise 2048 chunks, carte128, couleurs de biome/pentes/eau, zoom conserve ancienne texture à l'échelle. Paquet Point authentifié choisit hauteur mémorisée. MissileTelemetry fournit les seuls vols du lanceur et leur vitesse, flèches cyan dans TacticalScreen. Protocole4 et carte2.

Compatibilité : ClientCullingCompatibility utilise les champs publics EntityCullingModBase.entityWhitelist et tickCullWhitelists (API1.11.3 inspectée) pour véhicule, missile, bouchon. Repli lerpTo applique les positions réseau quand les ticks visuels sont suspendus >2ticks. CreateBetterFps1.1.2 modifie les buffers Create, pas la physique. Profil -PperformanceTest=true prépare Create6.0.8, CreateBetterFps1.1.2, EntityCulling1.11.3 et active Oculus1.8/Embeddium0.3.31. Tous restent séparés du JAR.

Les essais serveur précédents ont révélé et permis de corriger : collision avec ses propres sections, placement objet trop court, déploiement caisse SBW trop court. Tous ces scénarios passaient lors du dernier essai SBW. Il restait le test montagne qui heurtait le plafond naturel au-delà de l'arène souterraine ; fixture déplacée en ciel ouvert avec la même crête et les mêmes assertions, à valider dans le run preview-core. Ne pas retirer les assertions.

À poursuivre après l'intermédiaire : terminer matrice serveur SBW0.8.9.2 et JAR utilisateur95, test graphique réel avec -PperformanceTest=true, vérifier whitelist active dans log et vol en mouvement/collision/map/clic/icons. Aucun client0.5 n'a encore été lancé. Pas de certification du modpack utilisateur. Xenon reste reporté, autorisé par l'utilisateur.

Les modèles CGTrader ignorés sont présents localement ; ne pas supprimer ni forcer dans Git. Pas de ressources SBW copiées. Source des nouvelles icônes dans scripts/assets/create_item_icons.py. Les données officielles des mods de performance sont dans docs/PERFORMANCE-TEST-ARTIFACTS.json ; fichiers de recherche hors repo /workspace/research/compat05.

Source et JAR de préversion doivent être conservés sur GitHub avec un nom preview1. Version stable précédente0.4 reste disponible. Build : source scripts/cloud-env.sh puis ./gradlew --no-daemon build, un seul Gradle à la fois. Attendre exit0 pour copier le JAR. Vérifier téléchargement public et SHA256 après push.

Résultat final avant livraison intermédiaire : build +39/39 tests noyau sansSBW réussis, exit0, run/upgrade05-preview-checked et docs/test-results/upgrade-0.5/preview-core.log. Assertion de conservation de la crête corrigée au nouvel offset ; interception et dégâts réels confirmés. JAR110classesJava17. Les nouveaux marqueurs de missiles et l'intégration Entity Culling restent à vérifier en client réel.
