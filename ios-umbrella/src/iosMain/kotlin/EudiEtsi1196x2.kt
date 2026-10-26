/*
 * Copyright (c) 2024 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Umbrella framework marker file.
// This minimal source file triggers Kotlin Multiplatform framework compilation and linking.
// The actual public API is provided by the transitive dependencies:
// - etsi-1196x2-consultation: Core certificate validation abstractions
// - etsi-119602-consultation: LoTE-based validation (primary API)
// - etsi-119602-data-model: ETSI TS 119 602 data structures
// The Kotlin/Native linker automatically includes all transitive dependencies into the framework.
