# Changelog

## [0.1.6](https://github.com/yschimke/cadence/compare/v0.1.5...v0.1.6) (2026-10-30)


### Features

* drop platform notice and hide Bluetooth where unsupported ([#410](https://github.com/yschimke/cadence/issues/410)) ([18ec4a3](https://github.com/yschimke/cadence/commit/18ec4a31d834d719912a64f919b98573362720fb))
* package the browser app as an installable Wasmo app ([#417](https://github.com/yschimke/cadence/issues/417)) ([d763627](https://github.com/yschimke/cadence/commit/d7636278abfdd09f72fc188f980a42beeff275d4))
* share Cadence's settings across browsers on Wasmo ([#418](https://github.com/yschimke/cadence/issues/418)) ([6926d69](https://github.com/yschimke/cadence/commit/6926d69791c0529072a36cd58addcc64350b8708))


### Bug Fixes

* **deps:** update dependency io.ktor:ktor-client-core to v3.6.1 ([#416](https://github.com/yschimke/cadence/issues/416)) ([d085c8f](https://github.com/yschimke/cadence/commit/d085c8f2f7f92eab2f37d20f5b8e0dbded2dfcb8))

## [0.1.5](https://github.com/yschimke/cadence/compare/v0.1.4...v0.1.5) (2026-10-28)


### Features

* content-first library home with one way to add sources ([#405](https://github.com/yschimke/cadence/issues/405)) ([468a53d](https://github.com/yschimke/cadence/commit/468a53d327f583414714068b2d316b0e895b4ffb))
* sign in to Pocket Casts and import Up Next, in progress and playlists ([#404](https://github.com/yschimke/cadence/issues/404)) ([04ec3c8](https://github.com/yschimke/cadence/commit/04ec3c88b553fd817b44b0cea122872fd126e82e))


### Bug Fixes

* **deps:** update dependency ee.schimke.composeai:rc-widget-launcher to v2.21.0 ([#407](https://github.com/yschimke/cadence/issues/407)) ([1ceaf83](https://github.com/yschimke/cadence/commit/1ceaf839c2911fb1d8e2633fb53b5d046c278bea))


### Performance Improvements

* **compose:** add contentType to lazy list items and dedupe profile lookup ([#408](https://github.com/yschimke/cadence/issues/408)) ([d0c509b](https://github.com/yschimke/cadence/commit/d0c509ba627a075652736517ea3964e9fb1953e7))

## [0.1.4](https://github.com/yschimke/cadence/compare/v0.1.3...v0.1.4) (2026-10-28)


### Features

* import podcasts from gpodder, Nextcloud, Overcast and RSS ([#403](https://github.com/yschimke/cadence/issues/403)) ([3bb2d71](https://github.com/yschimke/cadence/commit/3bb2d71a55a098187a1a2838a05f786f67136eb0))


### Bug Fixes

* read the Pocket Casts web player's playlist and episode endpoints ([#400](https://github.com/yschimke/cadence/issues/400)) ([a22ab86](https://github.com/yschimke/cadence/commit/a22ab86b5c08fbc569352f113ca9b3c73eb6a9cc))

## [0.1.3](https://github.com/yschimke/cadence/compare/v0.1.2...v0.1.3) (2026-10-27)


### Features

* add desktop and browser support with USB discovery ([#352](https://github.com/yschimke/cadence/issues/352)) ([fed18d8](https://github.com/yschimke/cadence/commit/fed18d8099e2fe3b79b604d151e0c955e352f0a8))
* add podcast preparation and safe headphone sync ([#390](https://github.com/yschimke/cadence/issues/390)) ([b8649b1](https://github.com/yschimke/cadence/commit/b8649b11bc785848e8fa42ca2ff5e75b53d88c0b))
* automate internal Play releases with Firebase telemetry ([#394](https://github.com/yschimke/cadence/issues/394)) ([fb421d4](https://github.com/yschimke/cadence/commit/fb421d46bb2925d2cff23002e3622e202873f930))
* **catalog:** add a theme-foundation showcase to the design catalog ([#109](https://github.com/yschimke/cadence/issues/109)) ([4e0f052](https://github.com/yschimke/cadence/commit/4e0f05287f1f4930d90daaea3b7a87832b5adcb8))
* **design-artifacts:** add cadence catalog spec and delivery workflow ([#108](https://github.com/yschimke/cadence/issues/108)) ([7736c33](https://github.com/yschimke/cadence/commit/7736c3390ed22fccd45e9a83c321f7dc9187f3e8))
* draw the podcast widget with Remote Compose on Android 16+ ([#397](https://github.com/yschimke/cadence/issues/397)) ([5d31293](https://github.com/yschimke/cadence/commit/5d31293def508ddbb06baad67631f37f59a9c447))
* improve podcast sync continuity and investigate Pocket Casts imports ([#392](https://github.com/yschimke/cadence/issues/392)) ([cd52bb3](https://github.com/yschimke/cadence/commit/cd52bb398de520fecf62e02cdadb61be456cf597))


### Bug Fixes

* **build:** target Java 17 bytecode for the preview render daemon ([#147](https://github.com/yschimke/cadence/issues/147)) ([d76c8d4](https://github.com/yschimke/cadence/commit/d76c8d43de0703b9f717fe63607aa1a0fb586f17))
* **deps:** consolidate dependency updates and raise compileSdk to 37 ([#247](https://github.com/yschimke/cadence/issues/247)) ([27bedf0](https://github.com/yschimke/cadence/commit/27bedf0c1fdded380e30db2b2aa8422006f17f78))
* **deps:** update androidx ([#329](https://github.com/yschimke/cadence/issues/329)) ([c305ba1](https://github.com/yschimke/cadence/commit/c305ba1271b03372ebaaadb2827faf6f6924245b))
* **deps:** update androidx ([#92](https://github.com/yschimke/cadence/issues/92)) ([190a2dc](https://github.com/yschimke/cadence/commit/190a2dcc389634c2b5a4c172ffe9e485970ee4da))
* **deps:** update dependency androidx.compose.ui:ui-text-google-fonts to v1.11.1 ([#74](https://github.com/yschimke/cadence/issues/74)) ([234e0da](https://github.com/yschimke/cadence/commit/234e0daa1b93a16597b79b0afdfde73d4fc6fbfc))
* **deps:** update dependency androidx.compose.ui:ui-text-google-fonts to v1.11.2 ([#80](https://github.com/yschimke/cadence/issues/80)) ([2932f3f](https://github.com/yschimke/cadence/commit/2932f3fb2349a24f3e7095ce63f611be380d66f2))
* **deps:** update dependency androidx.compose.ui:ui-text-google-fonts to v1.12.1 ([#301](https://github.com/yschimke/cadence/issues/301)) ([b268afd](https://github.com/yschimke/cadence/commit/b268afdbb671fb089f9e5cf81af1184f52af5f0a))
* **deps:** update dependency androidx.constraintlayout:constraintlayout to v2.2.2 ([#175](https://github.com/yschimke/cadence/issues/175)) ([3f99e40](https://github.com/yschimke/cadence/commit/3f99e4062e13e2ed85d4adae91d447ddefba8a7d))
* **deps:** update dependency androidx.datastore:datastore to v1.3.0 ([#380](https://github.com/yschimke/cadence/issues/380)) ([c430081](https://github.com/yschimke/cadence/commit/c4300814fcca1f999b6d7bdc720e468952a6c15f))
* **deps:** update dependency com.google.android.material:material to v1.14.0 ([#83](https://github.com/yschimke/cadence/issues/83)) ([769a141](https://github.com/yschimke/cadence/commit/769a14169b8b69baca858599839afebc5b8fc8ae))
* **deps:** update dependency com.squareup.okio:okio to v3.18.1 ([#158](https://github.com/yschimke/cadence/issues/158)) ([b23e911](https://github.com/yschimke/cadence/commit/b23e911918780602d6f0663f938a1c151701cb14))
* **deps:** update dependency com.squareup.okio:okio to v3.18.2 ([#284](https://github.com/yschimke/cadence/issues/284)) ([5e77cd2](https://github.com/yschimke/cadence/commit/5e77cd24e67a214731d41bd08247dfc8027dc3b3))
* **deps:** update dependency io.ktor:ktor-client-core to v3.5.0 ([#84](https://github.com/yschimke/cadence/issues/84)) ([d7f4f20](https://github.com/yschimke/cadence/commit/d7f4f2043453b7e55ebb088e1c9bf38312b16d64))
* **deps:** update dependency io.ktor:ktor-client-core to v3.5.1 ([#112](https://github.com/yschimke/cadence/issues/112)) ([aa1145f](https://github.com/yschimke/cadence/commit/aa1145fdb540c5a3e2bb2a3fc44d9ef23062f736))
* **deps:** update dependency io.ktor:ktor-client-core to v3.5.2 ([#176](https://github.com/yschimke/cadence/issues/176)) ([8368ea7](https://github.com/yschimke/cadence/commit/8368ea723cfbb3a092eac30927aa1f836509f795))
* **deps:** update dependency io.ktor:ktor-client-core to v3.6.0 ([#316](https://github.com/yschimke/cadence/issues/316)) ([1064d47](https://github.com/yschimke/cadence/commit/1064d47b227c4f9e3604a97a143d1ed7268619fb))
* **deps:** update dependency org.jetbrains.androidx.navigation:navigation-compose to v2.10.0 ([#386](https://github.com/yschimke/cadence/issues/386)) ([63c592f](https://github.com/yschimke/cadence/commit/63c592f3de8a74283be364be009f832aac848635))
* **deps:** update dependency org.jetbrains.kotlinx:kotlinx-coroutines-android to v1.11.0 ([#75](https://github.com/yschimke/cadence/issues/75)) ([f8d61ed](https://github.com/yschimke/cadence/commit/f8d61ed2e9f738456c5d9a737f39d3259e8add90))
* **deps:** update dependency org.jetbrains.kotlinx:kotlinx-serialization-json to v1.12.0 ([#373](https://github.com/yschimke/cadence/issues/373)) ([7bf3a0a](https://github.com/yschimke/cadence/commit/7bf3a0a4d19ae63f143cd511dfded713dc56c27b))
* **deps:** update kotlin to v2.4.0 ([#93](https://github.com/yschimke/cadence/issues/93)) ([64d31b3](https://github.com/yschimke/cadence/commit/64d31b3abcffb72214b2945549d3480e6b89df5a))
* **deps:** update kotlin to v2.4.10 ([#137](https://github.com/yschimke/cadence/issues/137)) ([13ec431](https://github.com/yschimke/cadence/commit/13ec431f5363ebc28d7c3e93532bb9964806f801))
* **deps:** update kotlin to v2.4.20 ([#302](https://github.com/yschimke/cadence/issues/302)) ([9eebedb](https://github.com/yschimke/cadence/commit/9eebedb84c565cff7a914320f2ebe85c7d5f2898))
* **deps:** update modernstoragestorage to v1.0.0-alpha12 ([#132](https://github.com/yschimke/cadence/issues/132)) ([81c6372](https://github.com/yschimke/cadence/commit/81c6372dccc43f7b5cd609ae384b506155be423d))
* **design-artifacts:** publish a live bundle for the cadence catalog ([#160](https://github.com/yschimke/cadence/issues/160)) ([22d8722](https://github.com/yschimke/cadence/commit/22d87223c792f6b2764b731e80ca14eee905518e))
* **devcontainer:** allow Plugin Portal artifact redirects ([#367](https://github.com/yschimke/cadence/issues/367)) ([bfa7331](https://github.com/yschimke/cadence/commit/bfa7331c376380bd2c89611d828845b045422578))
* **devcontainer:** fail provisioning errors and allow build repositories ([#366](https://github.com/yschimke/cadence/issues/366)) ([ed827e3](https://github.com/yschimke/cadence/commit/ed827e314c76dad6a40e305205c7d02914f0415b))
* remove advertising permissions from Firebase builds ([#395](https://github.com/yschimke/cadence/issues/395)) ([b2b1014](https://github.com/yschimke/cadence/commit/b2b1014b5d3501319b0172b98eabdeec6e92d7ac))
* **scripts:** render play screenshots via compose-preview CLI ([#98](https://github.com/yschimke/cadence/issues/98)) ([651a300](https://github.com/yschimke/cadence/commit/651a30051f91c12aa1c8ccbad580eca40f80b697))
* **ui:** clear up library/sync/bluetooth UX rough edges ([#57](https://github.com/yschimke/cadence/issues/57)) ([34becd2](https://github.com/yschimke/cadence/commit/34becd20a1e394a52267c63cdf2184abe400de6e))
