package me.uc.hussein.ultrasdiscord.model;

import java.util.List;
import java.util.Map;

/**
 * Result handed from a detector to the AlertManager.
 *
 * @param suspicion 0..100
 * @param reason    main human readable reason (plain text)
 * @param reasons   every contributing reason (plain text)
 * @param extra     extra placeholders (type specific, e.g. diamond, cps...)
 */
public record Detection(double suspicion, String reason, List<String> reasons, Map<String, String> extra) {
}
