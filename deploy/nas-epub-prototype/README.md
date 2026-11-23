# Prototype EPUB sur NAS

Ce prototype ponctuel traite uniquement des EPUB synthétiques marqués. Il ne
communique ni avec Grimmory ni avec sa base et ne doit pas recevoir de livres
réels. Il utilise le moteur EPUB existant avec des métadonnées partielles :
la préservation de toutes les métadonnées d'un livre réel n'est pas garantie.

Le programme lit une requête JSON sur son entrée standard et crée un nouveau
fichier EPUB ; il ne remplace jamais l'entrée ni un fichier existant. Il limite
la taille de la requête et de l'archive, vérifie le SHA-256 de l'entrée,
contrôle les chemins et les sommes CRC des entrées ZIP, et refuse les EPUB sans
le fichier `META-INF/grimmory-nas-prototype.txt` contenant exactement
`synthetic-test-only-v1`.

Le protocole de test accepte aussi un instantané complet des métadonnées et
les indicateurs d'effacement et une couverture en base64, afin de comparer le
résultat avec le moteur local. Les livres réels restent interdits.
Pour une requête limitée au titre et aux auteurs, les métadonnées
`booklore:*` existantes sont conservées. Les champs `booklore:*` inconnus
restent aussi présents lors d'une mise à jour complète. Pour les séries
EPUB3 identifiées comme telles, la mise à jour conserve les autres
collections et les raffinements non ciblés.

Un éventuel dossier de travail distinct doit rester hors des chemins surveillés
par Grimmory. Un remplacement atomique exige aussi que les fichiers déplacés
soient sur le même système de fichiers ; un partage ou montage distinct peut
empêcher ce remplacement. Ce dossier ne remplace ni la coordination avec les
scans ni la reprise après interruption. Aucune configuration de production
n'est fournie par ce prototype.

## Préparation

1. Construire le frontend puis le JAR backend pour Linux x86-64, avec
   `TARGETPLATFORM=linux/amd64`, `TARGETARCH=amd64` et `TARGETLIBC=gnu`.
2. Créer un dossier de projet dans un partage NAS dédié aux tests, distinct de
   toutes les bibliothèques. Y placer `compose.yaml`, un dossier `package`
   contenant `Dockerfile`, le JAR sous le nom `backend.jar` et `request.json`,
   ainsi qu'un dossier `sandbox` contenant uniquement l'EPUB synthétique.
3. Créer le fichier `.env` dans le dossier du projet :

   ```text
   PROTOTYPE_UID=<UID du compte de test>
   PROTOTYPE_GID=<GID du compte de test>
   ```

   Vérifier que ce compte non administrateur peut lire le paquet et lire et
   écrire dans `sandbox`. Ne pas ajouter ce compte au groupe Docker.
4. Le JSON de test contient les noms simples des deux fichiers, le SHA-256 de
   l'entrée et au moins un titre ou une liste d'auteurs :

   ```json
   {"input":"input.epub","output":"output.epub","inputSha256":"<sha256>","title":"Synthetic title","authors":["Synthetic author"]}
   ```

   Pour tester d'autres champs, remplacer `title` et `authors` par un objet
   `metadata` correspondant à un instantané complet, et ajouter si besoin
   `clearFlags` et `cover` (image encodée en base64, 4 Mio maximum). Ne pas
   mélanger ces deux formes. Une requête ne peut pas dépasser 6 Mio.

## Exécution

Dans Container Manager, créer un projet à partir du fichier `compose.yaml`
placé dans le dossier de projet, puis construire et démarrer le service. La
construction télécharge l'image Java publique identifiée par son empreinte.
Le conteneur lancé est ponctuel, sans réseau, sans privilèges supplémentaires,
avec un système racine en lecture seule et uniquement le dossier de test monté
en écriture. Son journal doit contenir un objet JSON avec `output` et
`outputSha256`. Une erreur est signalée par une sortie non nulle.

La mémoire et le nombre de processus sont limités, mais pas le temps CPU :
le noyau Synology testé ne prend pas en charge les quotas CPU Docker.

Le fichier `output.epub` doit apparaître dans `sandbox` ; vérifier son SHA-256
et que celui d'`input.epub` est inchangé. Le programme refuse de remplacer un
résultat déjà présent. Ne pas redémarrer le projet avec le même nom de sortie.

Ce test n'établit pas l'isolation d'un futur service réseau ni la cohérence
entre un fichier et la base Grimmory. La compatibilité complète et le
remplacement sûr des fichiers restent à valider avant toute utilisation réelle.

## Liaison réseau expérimentale

Le code contient également un serveur et un client HTTPS expérimentaux,
limités au même bac à sable et aux EPUB synthétiques marqués. Le serveur
exige un certificat client ; le client vérifie le certificat et le nom du
serveur et ne suit pas les redirections. Les erreurs sont remontées sans
repli vers une écriture locale.

L'endpoint expérimental `/v1/synthetic-batch` accepte jusqu'à 100 requêtes
dans un objet `{"requests":[...]}`. En cas d'erreur pendant la préparation,
il tente de supprimer les sorties déjà créées et signale tout échec de
nettoyage. Il ne modifie aucun fichier d'entrée. Une interruption du
processus ou une perte de réponse peut néanmoins laisser des sorties
temporaires : ce mécanisme n'est pas une transaction durable.

L'endpoint `/v1/synthetic-job` prépare un groupe sous un identifiant UUID
dans `.grimmory-synthetic-jobs`. Une nouvelle tentative avec le même
identifiant et la même requête retrouve le résultat vérifié ou reprend une
préparation interrompue ; une requête différente est refusée. Les candidats
et journaux ne sont pas supprimés automatiquement. Ils peuvent donc occuper de
l'espace après une erreur ou une annulation de la base.

Un remplacement réversible de lots synthétiques est également testé
localement. Il conserve les sauvegardes et journalise les états avant de
modifier les fichiers ; une reprise explicite peut restaurer les fichiers
identifiés par leur empreinte, sans écraser un contenu inattendu. Il n'est
pas intégré aux parcours Grimmory. Le verrou ne
coordonne que les participants à ce prototype, pas les autres écritures de
Grimmory. Une réservation durable empêche les travaux de remplacement
synthétiques de se chevaucher dans un même bac à sable, jusqu'à la
restauration ou la finalisation. Les copies ne préservent pas les ACL ni les
autres attributs par défaut.
Il refuse les fichiers de plus de 512 Mio lors du remplacement ou du retour
arrière ; la limite d'entrée du préparateur reste de 64 Mio.
Les interruptions sont notamment simulées dans les tests automatisés ; la
sonde NAS ci-dessous arrête aussi une JVM pendant un remplacement. Cela ne
garantit pas la résistance aux coupures électriques.

Pour les tests isolés uniquement, définir
`NAS_EPUB_AGENT_SYNTHETIC_REPLACEMENT=true` active les endpoints mTLS
`/v1/synthetic-replacement/prepare`, `/apply`, `/finish`, `/rollback` et
`/cancel-preparation` (les quatre derniers sous le même préfixe). Ils reçoivent chacun la requête complète du travail
synthétique et renvoient son UUID et son état. Sans cette variable ou avec
`false`, ils sont désactivés ; toute autre valeur empêche le démarrage.
Il faut conserver la requête. En utilisation directe, la décision de reprendre
ou de restaurer après une réponse perdue appartient au client. Ne pas monter
la bibliothèque réelle. Ne jamais appeler `finish` sans avoir établi que la
décision de base est `COMMITTED` ; l'agent ne vérifie pas lui-même la base.
De même, `cancel-preparation` ne doit être appelé qu'après avoir établi la
décision `ABORT_PREPARING`, jamais pour un travail dont l'application a pu
être décidée en base.

Pour les tests Synology isolés, l'option supplémentaire
`NAS_EPUB_AGENT_SYNTHETIC_SYNOLOGY_ACL=true` active la capture et la
vérification de l'UID, du GID, du mode, des entrées ACL et des indicateurs ACL.
Elle exige l'activation du remplacement synthétique, l'outil monté en lecture
seule dans `/opt/synology/synoacltool` et ses bibliothèques dans
`/opt/synology/lib`. Le répertoire des journaux doit être protégé : les
instantanés ACL y sont conservés. Une modification inattendue est refusée,
et une opération commencée avec cette option ne peut pas reprendre sans elle.
Ce mode est désactivé par défaut et ne préserve pas les autres attributs.
Le montage d'une bibliothèque réelle reste interdit.

Pour les seuls essais synthétiques, `NAS_EPUB_AGENT_SYNTHETIC_STATE_DIR` peut
désigner un répertoire absolu existant, séparé du bac à sable et monté en
écriture. Les copies préparées, journaux et sauvegardes y sont conservés ; les
marqueurs d'identité et la réservation restent dans le bac à sable. Le
répertoire doit être dédié à un seul bac à sable, persistant et accessible
uniquement au compte de l'agent : son propriétaire, son mode et ses ACL doivent
être vérifiés par l'administrateur. L'application ne certifie pas la
confidentialité des ACL du répertoire.

Le chemin et l'identité du bac à sable sont liés durablement à cet état.
Une configuration différente, un répertoire réutilisé pour un autre bac à
sable ou un marqueur manquant sont refusés. Sans cette variable, l'ancien
stockage dans `.grimmory-synthetic-jobs` reste utilisé. Un bac à sable contenant
déjà des travaux dans cet ancien emplacement ne peut pas être basculé
automatiquement : terminer ou reprendre les travaux et créer un nouveau bac
à sable de test. Ne pas déplacer ni supprimer les marqueurs pour contourner ce
contrôle. Cette option n'a pas encore été validée sur Synology et ne rend pas
l'agent apte aux livres réels.

Un coordinateur distinct expérimental associe ces endpoints à une décision en
base : `PREPARING`, `PENDING`, `COMMITTED`, `ABORT_PREPARING`, `ABORTING` ou `ABORTED`. Il
enregistre la requête et l'état `PREPARING` avant de préparer les copies, puis
applique les modifications dans une
transaction de base et, en cas d'erreur, consulte la décision sous verrou avant
de tenter une restauration. Une décision `COMMITTED` n'est pas annulée même si
l'accusé de réception du commit a été perdu. Si la base ou l'agent est
indisponible, l'erreur est remontée et la récupération doit être relancée
explicitement ; `recoverAbandoned` annule les travaux `PREPARING`, `PENDING`,
`ABORT_PREPARING` ou `ABORTING`
et ne doit être appelé qu'après avoir arrêté les producteurs et confirmé que
tous les travaux non terminés sont abandonnés. Pour un travail `PREPARING`, il
enregistre `ABORT_PREPARING`, puis annule la préparation sans exiger que les
entrées restent accessibles. Un marqueur durable empêche toute reprise du même
UUID lorsque le journal de remplacement n'existe pas encore. Si l'agent est
indisponible, la décision et la réservation restent à reprendre. Les sauvegardes
ne sont pas supprimées automatiquement.
Lors de la préparation d'un nouveau travail, le coordinateur reprend d'abord
une décision déjà prise mais non finalisée : `COMMITTED`, `ABORT_PREPARING` ou
`ABORTING`. Si l'agent est indisponible, le nouveau travail est refusé et la
réservation est conservée. Cette reprise à la demande n'annule jamais un
travail `PREPARING` ou `PENDING`, qui peut encore être actif. Elle ne remplace
pas une reprise automatique au démarrage ni une procédure pour identifier les
travaux abandonnés.
Pour reprendre une préparation dont la réponse ou l'enregistrement est
incertain, réutiliser exactement la même requête et le même UUID ; une
réservation orpheline ne doit pas être supprimée manuellement sans
vérification de la décision.
Une réservation en base est créée dans la même transaction que la décision
`PREPARING` et reste détenue jusqu'à la fin de la restauration ou de la
finalisation. Un participant expérimental peut vérifier cette réservation sous
verrou dans sa propre transaction d'écriture. Cette protection est globale à
la base de test et ne concerne que les participants qui l'utilisent. Pendant
les modifications de base coordonnées, le propriétaire de la réservation peut
franchir ce garde uniquement dans la transaction courante ; cette autorisation
est retirée à la sortie du traitement, y compris en cas d'erreur. Une transaction
indépendante imbriquée reste refusée.
Les services de rattachement, de détachement, de remplacement de contenu, de
suppression, de déplacement et de gestion des métadonnées appellent un point
de coordination. Les mises à jour individuelles et par lot de
`BookMetadataService`, les modifications des verrous de métadonnées et les
imports et exports sidecar le prennent avant de lire les livres. Pour les lots
de métadonnées, chaque transaction indépendante prend le verrou avant sa
lecture et signale tout refus dans son résultat. Les mises à jour individuelles
de couverture le prennent également avant lecture ; pour les traitements
asynchrones, chaque livre est relu sous verrou dans sa propre transaction et
les refus sont signalés dans les notifications de progression. La réparation
EPUB recharge également chaque fichier actif sous verrou dans une transaction
distincte et compte les refus comme des échecs. Si la mise à jour de son
empreinte échoue après remplacement du fichier, la sauvegarde de réparation
reste disponible, mais la cohérence nécessite une intervention : la réparation
existante ne restaure pas automatiquement ce fichier.
Le rafraîchissement des métadonnées prend aussi le verrou avant de charger
chaque livre ; un refus échoue la tâche. Il démarre hors transaction existante,
annule la transaction du livre si une exception remonte et arrête la tâche ;
les notifications de réussite du livre sont envoyées après validation de la
transaction. Les erreurs d'écriture encore interceptées par le service de
mise à jour ne remontent pas toutes, et une annulation de base ne restaure pas
un fichier déjà modifié : ce parcours ne fournit donc pas encore les garanties
nécessaires pour la bêta sur les livres réels. Le rescan prend le verrou avant de charger la
bibliothèque et signale un refus. Ces deux parcours conservent leurs transactions
existantes : le verrou global pourrait être retenu pendant des appels réseau ou
pendant tout le rescan. Il faut réduire ces durées avant d'activer un participant,
en particulier pour les grandes bibliothèques.
La finalisation des imports Bookdrop prend également le verrou avant de lire
les éléments à importer et de suspendre la surveillance ; un refus interrompt
l'opération avant toute copie. La transaction existante couvre tout le lot et
peut donc conserver ce verrou longtemps. Les autres imports et les scans et
événements de surveillance ne sont pas encore tous coordonnés.
Chaque groupe de nouveaux livres vérifie le verrou dans sa transaction
indépendante avant de lire la bibliothèque ; un refus remonte au lieu d'être
ignoré. Le scan peut toutefois avoir déjà effectué d'autres opérations et
les événements de surveillance ne sont pas automatiquement rejoués après
un refus : cela ne coordonne pas le scan entier.
Si le même fil reprend ce verrou dans une transaction indépendante imbriquée
sur la même source de données, le verrou expérimental refuse immédiatement
l'opération au lieu d'attendre sa propre transaction parente. Ce refus ne
coordonne pas les parcours manquants et ne remplace pas leur découpage en
transactions sûres ; il ne rend pas le participant activable en production.
L'actualisation des métadonnées et le déplacement individuel peuvent toutefois
recevoir un livre déjà chargé par d'autres appelants : ceux-ci doivent encore
être coordonnés avant cette lecture pour éviter les données périmées. D'autres
parcours restent également à raccorder. Aucun participant n'est enregistré en
production : ce point ne bloque donc rien par défaut. Les tests injectent
notamment la réservation synthétique pour le rattachement, le détachement,
les déplacements, les métadonnées, les sidecars, les couvertures et la
réparation ; cela ne constitue pas une intégration complète de l'agent.
Après un commit, le coordinateur finalise l'agent et libère la réservation.
Si cette réponse est perdue, relancer `process` avec le même UUID : les
modifications de base ne sont pas répétées. `recoverCommitted` finalise aussi,
par lots de 1 à 100, les décisions `COMMITTED` non encore marquées comme
finalisées en base ; une erreur interrompt le lot et reste à reprendre
explicitement. La finalisation est idempotente et ne restaure jamais une
décision validée. Ce coordinateur utilise un schéma réservé aux tests et n'est
pas intégré à Grimmory ; aucune reprise automatique n'est encore configurée.
La préparation des métadonnées issues des fournisseurs est désormais séparée
de leur application dans le service Grimmory. Un test local raccorde un titre
fourni par un fournisseur simulé à l'agent mTLS et à une base H2 de test ; il
vérifie la restauration après échec et la poursuite sur un autre EPUB
synthétique. Ce raccordement existe uniquement dans le test : le coordinateur
ne l'utilise pas en production, et le véritable service de mise à jour des
livres n'est pas appelé. Cela ne coordonne pas les écritures réelles et ne
rend pas le mode maintenance déployable.

### Tests MariaDB isolés

`mariadb-coordinator-test.compose.yaml` lance une base jetable en mémoire, sans
montage de livres ni de données de production. Préparer un dossier de projet
privé contenant ce fichier sous le nom `compose.yaml`, `mariadb-test-init.sql`
et un `.env` privé avec un mot de passe aléatoire `PROBE_DB_PASSWORD`. Depuis
`backend`, construire le paquet de tests :

```text
.\gradlew.bat nasMariaDbTestBundle --no-daemon
```

Extraire `build/nas-mariadb-test/nas-mariadb-test-bundle.zip` dans le dossier
`test-bundle` du projet NAS, puis démarrer ce projet dans Container Manager.
La base et le conteneur de test communiquent sur un réseau Docker interne,
sans port publié. La base est en mémoire et ses données disparaissent à
l'arrêt du conteneur. Le journal de `coordinator-test` doit indiquer douze
tests réussis, aucun échec et aucune désactivation.

Les tests refusent une autre base ou l'absence du marqueur de test avant de
créer leurs tables. Ils vérifient les transactions InnoDB, la reprise d'un
commit et le verrouillage concurrent. Dix scénarios utilisent un agent simulé ;
les deux autres utilisent le véritable agent HTTPS mTLS sur l'interface locale du
conteneur et des EPUB synthétiques temporaires, puis vérifie la restauration
après échec et la reprise après une réponse de commit perdue et un redémarrage
de l'agent ainsi que l'annulation d'une préparation inachevée. Les certificats
sont générés uniquement pour ces tests. Cela ne valide
ni une liaison entre machines, ni les ACL Synology, ni les EPUB réels. Les
tests sont désactivés par défaut. Utiliser une nouvelle base jetable pour
chaque campagne et arrêter le projet après le test.

Le coordinateur expérimental enregistre les requêtes synthétiques dans
`nas_synthetic_job`. Son mode transactionnel annule les modifications de base
si la préparation échoue ; les opérations déjà enregistrées en attente peuvent
être reprises explicitement. L'état `DONE` signifie uniquement que les copies
synthétiques sont préparées, pas que les livres ont été remplacés. Aucune
reprise n'est encore planifiée automatiquement au démarrage.
Le schéma de cette table est limité aux ressources de test : aucune migration
Flyway de production ne l'installe. Il ne faut pas utiliser le coordinateur
avec la base de production ; une migration adaptée sera nécessaire avant toute
intégration réelle.

Le serveur exige `NAS_EPUB_AGENT_BIND` et `NAS_EPUB_AGENT_PORT`, ainsi que les
fichiers PKCS#12 `keystore.p12` et `truststore.p12` et leurs fichiers de mots
de passe `keystore-password` et `truststore-password` dans `/run/secrets`.
Les clés et mots de passe doivent rester hors du dépôt et accessibles
uniquement au compte de l'agent. Le réseau doit être limité aux clients
autorisés par un pare-feu ; TLS ne remplace pas cette restriction.

Cette liaison est testée localement mais n'est ni intégrée à Grimmory ni prête
pour la production. Si le remplacement synthétique est activé, elle modifie
les fichiers d'entrée de test. L'intégration des transactions, l'exclusion
avec les autres écritures, la conservation des permissions et la reprise
après panne restent indispensables.
La résistance à une coupure électrique n'a pas été vérifiée sur le NAS. Sous
Windows, la synchronisation durable des répertoires n'est pas garantie.

### Test d'écriture sur des copies réelles d'EPUB

`real-epub-copy-test.compose.yaml` vérifie le moteur d'écriture sur exactement
trois copies d'EPUB réels. Copier les fichiers depuis le partage de bibliothèque
dans `inputs` sans les déplacer ; ne jamais monter le partage de bibliothèque
dans le projet ou dans le conteneur. Conserver les originaux en lecture seule et
vérifier leurs empreintes SHA-256 avant et après la copie. Le conteneur monte
`inputs` en lecture seule et écrit uniquement dans `work`.

Construire `nasMariaDbTestBundle` pour inclure le runner, extraire le paquet dans
`test-bundle`, et créer `work/tmp`. Le dossier `work` doit appartenir à l'UID
1031/GID 100 utilisé par le projet ; ne pas modifier les ACL ou permissions des
livres sources. La campagne refuse les fichiers de plus de 200 Mio, les archives
qui dépassent 512 Mio décompressées, les dossiers d'entrée/écriture qui se
chevauchent et un dossier `work` non vide.

Dans Container Manager, démarrer le projet depuis le dossier isolé. Le
conteneur n'a pas de réseau, n'accède à aucune base ou fournisseur de
métadonnées et remplace uniquement le titre de chaque copie dans `work` par un
marqueur de test. Il vérifie les tailles et CRC des entrées ZIP, la présence du nouveau titre,
l'absence de changement dans les autres entrées ZIP, l'empreinte inchangée des
copies d'entrée, les éléments de métadonnées OPF non ciblés et l'absence de
fichiers temporaires. La comparaison ignore leur ordre et les espaces de
formatage. Le journal doit indiquer
`REAL_EPUB_COPY_TEST=PASSED`, trois copies écrites et trois entrées inchangées.
Arrêter le projet après la campagne.

Ce test valide le moteur EPUB sur des fichiers réels et sur le système de
fichiers du NAS uniquement. Il ne valide ni les fournisseurs en ligne,
l'ensemble de la structure et des attributs hors métadonnées du fichier OPF,
l'intégration à la base Grimmory, les ACL des livres de production, ni le
coordinateur de maintenance ; il ne rend pas sûre une écriture dans la
bibliothèque réelle.

## Vérification isolée de CAP_CHOWN

`capability-probe.compose.yaml` est un test ponctuel, sans réseau, qui ne
monte qu'un dossier `capability-sandbox` dédié et vide. Il nécessite dans
`.env` les variables `PROBE_GID`, `PROBE_OWNER_UID` et `PROBE_OWNER_GID`.
Il vérifie que la seule capacité effective est `CAP_CHOWN`, puis teste le
propriétaire et le groupe après remplacement de fichiers synthétiques. Ne pas
monter de bibliothèque réelle. Ce test n'établit ni la préservation des ACL
Synology ni la sécurité d'un agent de production.

`acl-capability-probe.compose.yaml` effectue un test complémentaire sur des
fichiers synthétiques, en montant uniquement l'outil ACL Synology et ses
bibliothèques en lecture seule. Les chemins de bibliothèques dépendent de DSM.
Les ACL utilisées par ce test ne prouvent pas que toutes les ACL réelles ou
les autres attributs d'un livre seront préservés.

`inherited-acl-probe.compose.yaml` vérifie séparément une ACL héritée et un
fichier synthétique créé au préalable dans `capability-sandbox/inherited` par
un autre utilisateur. Il remplace ce seul fichier de test ; il ne doit jamais
être pointé vers une bibliothèque.
Sur le NAS testé, `chmod` a supprimé l'ACL héritée d'un fichier synthétique ;
le test compare donc le mode après copie de l'ACL sans tenter de le corriger.
Tout écart est une erreur.

`acl-batch-rollback-probe.compose.yaml` teste séparément le remplacement et
la restauration de deux fichiers synthétiques avec ACL héritée et explicite,
en comparant propriétaire, groupe, mode, entrées et indicateurs d'ACL. Les
sauvegardes ne sont supprimées qu'après validation de la restauration. Il ne
valide pas les attributs non comparés ni la reprise après coupure.

`java-acl-probe.compose.yaml` et `java-explicit-acl-probe.compose.yaml` lancent
le code Java de remplacement et de restauration sur un EPUB synthétique marqué.
Le second vérifie l'empreinte fixe du fichier avant de lui attribuer l'identité
de test `1035:101`. Ces sondes nécessitent un JAR backend Linux placé dans le
projet et ne doivent monter qu'un dossier de test.

`java-acl-crash-probe.compose.yaml` teste la reprise dans une nouvelle JVM
après l'arrêt brutal volontaire de la première, immédiatement après le
remplacement du premier de deux EPUB synthétiques. Le projet doit contenir
`backend.jar`, `.env` avec `PROBE_GID`, et un dossier isolé `sandbox` avec
`first.epub` et `second.epub`, tous deux marqués et ayant le SHA-256 indiqué
dans le fichier Compose. Le premier doit appartenir à `1031:100` et avoir une
ACL héritée ; le second doit avoir une ACL explicite. Après vérification des
empreintes, le conteneur attribue uniquement au second l'identité de test
`1035:101`. Les chemins de l'outil ACL et de ses bibliothèques sont propres au
DSM testé. Ne jamais monter de bibliothèque réelle.

Le service attend le code de sortie `77` de la première JVM, puis restaure le
lot et affiche un JSON avec `processCrashRecovered`, `twoFilesRestored` et
`ownershipModeAclPreserved` à `true`. Vérifier ensuite les empreintes, les
identités, les ACL et la libération de la réservation ; conserver les journaux
dans un emplacement protégé. L'essai réussi sur le NAS avec une ACL héritée
et une ACL explicite ne démontre ni la résistance à une coupure électrique
ni la préservation des attributs non vérifiés.

## Conditions avant une mise en production

L'agent et son coordinateur ne sont pas intégrés aux écritures Grimmory et
ne doivent pas être activés sur les livres réels. Avant leur intégration :

- Le moteur EPUB refuse désormais d'écraser un fichier `.bak` ou `.tmp`
  existant et exige un remplacement atomique sur le système de fichiers.
  Une restauration échouée conserve la sauvegarde et signale l'erreur.
  Ces précautions ne vérifient pas les ACL ni ne coordonnent la base de données.
- Un chemin strict interne peut maintenant refuser les mises à jour non EPUB,
  les couvertures, les sidecars, les déplacements automatiques et toute
  erreur d'écriture EPUB masquée. Il n'est pas raccordé à une tâche et ne
  protège pas encore une écriture EPUB réussie suivie d'un échec du commit SQL.
- Pour une actualisation exceptionnelle de toute la bibliothèque, le mode
  envisagé met Grimmory en maintenance pendant le traitement. Les métadonnées
  proviennent des fournisseurs en ligne ; chaque livre doit être traité
  indépendamment, restauré en cas d'échec et ajouté au rapport des erreurs.
  Les EPUB invalides doivent être ignorés sans modifier leur fichier ni leur
  fiche et figurer dans le rapport. Les déplacements automatiques doivent être
  coordonnés avec les écritures et la base, et repris ou restaurés après une
  interruption ; ces garanties ne sont pas encore implémentées.
  La première bêta visée doit ignorer les formats autres qu'EPUB et les
  signaler dans le rapport ; ce filtrage n'est pas encore implémenté.
  Ce mode n'est pas encore implémenté. La mise en maintenance doit aussi
  arrêter et vérifier l'arrêt des tâches déjà actives, des scans et de la
  surveillance ; bloquer seulement les nouvelles requêtes ne suffit pas.
- Un orchestrateur séquentiel **synthétique** traite désormais une requête
  par livre. Il ne continue après une erreur que si l'annulation est confirmée,
  finalise une décision déjà validée sans rejouer la base et s'arrête si la
  décision ou la restauration reste incertaine. Les résultats sont enregistrés
  dans la base de test et peuvent être consultés par pages. Les entrées
  interrompues peuvent être réconciliées explicitement après arrêt de tous
  les producteurs ; cette reprise n'est ni automatique ni validée après
  redémarrage de la base. Le stockage de test n'est pas une migration de
  production. L'orchestrateur n'est pas raccordé aux fournisseurs ni à
  Grimmory ; il exige un producteur exclusif et des modifications de base
  dans la transaction du coordinateur.
- Intégrer et tester sur les parcours réels le remplacement et la
  restauration de lots, avec un journal durable, la reprise après interruption
  et une décision de base de données non ambiguë. Le coordinateur actuel ne
  remplace que des fichiers synthétiques.
- Coordonner les écritures et déplacements concurrents ; vérifier les
  empreintes et refuser toute modification inattendue.
- Étendre cette coordination, avant lecture des chemins et jusqu'à la fin des
  transactions concernées, aux mises à jour de métadonnées et de couvertures,
  aux déplacements, rattachements et détachements de fichiers, aux
  remplacements de contenu, suppressions, imports, réparations et traitements
  de surveillance ou de scan qui modifient les enregistrements associés.
  Les verrous locaux d'`ArchiveService` ne coordonnent pas ces parcours.
- Préserver et vérifier propriétaire, groupe, mode, ACL héritées et
  explicites ainsi que les autres attributs nécessaires, y compris lors de
  la restauration. Les sondes isolées ne couvrent que les cas testés.
- Valider sur des copies isolées du NAS les erreurs d'espace, permissions,
  réseau, redémarrage et coupure électrique, sans supprimer les sauvegardes
  dont l'état est incertain.
- Prévoir l'activation explicitement désactivée par défaut, le déploiement
  mTLS, la rotation des certificats, la supervision et une procédure de
  retour arrière. Une migration de production devra être conçue séparément.

L'emploi de `CAP_CHOWN` sur le montage des livres demande une autorisation
distincte ; les tests existants ne l'utilisent que sur des fichiers factices.
