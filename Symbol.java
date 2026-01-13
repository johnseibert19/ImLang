/**
 * Represents a single entry in the Symbol Table.
 * Tracks name, type, numeric scope level, specific scope label, and attributes.
 * @author John Seibert
 */
public record Symbol(
        String name,
        String type,
        int scopeLevel,
        String scopeLabel, // "Global", "Local", or "Block"
        String attribute
) {
    // Record automatically provides accessors.
}