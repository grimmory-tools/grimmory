import {Injectable} from '@angular/core';
import { sha256 } from '@noble/hashes/sha2.js';

@Injectable()
export class Digest {
  sha256(value: Uint8Array): Uint8Array {
    return sha256(value);
  }
}
