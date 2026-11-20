import {Service} from '@angular/core';
import { sha256 } from '@noble/hashes/sha2.js';

@Service()
export class DigestService {
  sha256(value: Uint8Array): Uint8Array {
    return sha256(value);
  }
}
