const MODIFIER_PLACEHOLDER_REGEX = /\{([^}:]+)(?::([^}]+))?}/g;

export function applyModifier(value: string, modifier: string, fieldName: string): string {
  if (!value) return value;

  switch (modifier) {
    case 'first':
      return value.split(', ')[0].trim();
    case 'sort': {
      const first = value.split(', ')[0].trim();
      const lastSpace = first.lastIndexOf(' ');
      return lastSpace > 0 ? `${first.substring(lastSpace + 1)}, ${first.substring(0, lastSpace)}` : first;
    }
    case 'initial': {
      let target = value;
      if (fieldName === 'authors') {
        const firstAuthor = value.split(', ')[0].trim();
        const lastSpace = firstAuthor.lastIndexOf(' ');
        target = lastSpace > 0 ? firstAuthor.substring(lastSpace + 1) : firstAuthor;
      }
      return target.charAt(0).toUpperCase();
    }
    case 'upper':
      return value.toUpperCase();
    case 'lower':
      return value.toLowerCase();
    default:
      return value;
  }
}

function resolveModifierPlaceholders(block: string, values: Record<string, string>): string {
  return block.replace(MODIFIER_PLACEHOLDER_REGEX, (_, fieldName: string, modifier?: string) => {
    const val = values[fieldName] ?? '';
    return modifier ? applyModifier(val, modifier, fieldName) : val;
  });
}

function checkAllPlaceholdersPresent(block: string, values: Record<string, string>): boolean {
  const matches = [...block.matchAll(MODIFIER_PLACEHOLDER_REGEX)];
  return matches.every(m => values[m[1]]?.trim());
}

export function replacePlaceholders(pattern: string, values: Record<string, string>): string {
  // Handle optional blocks with else clause: <primary|fallback>
  pattern = pattern.replace(/<([^<>]+)>/g, (_, blockContent: string) => {
    const pipeIndex = blockContent.indexOf('|');
    const primary = pipeIndex >= 0 ? blockContent.substring(0, pipeIndex) : blockContent;
    const fallback = pipeIndex >= 0 ? blockContent.substring(pipeIndex + 1) : null;

    if (checkAllPlaceholdersPresent(primary, values)) {
      return resolveModifierPlaceholders(primary, values);
    }
    return fallback != null ? resolveModifierPlaceholders(fallback, values) : '';
  });

  // Replace remaining top-level placeholders (with optional modifiers)
  return resolveModifierPlaceholders(pattern, values).trim();
}
