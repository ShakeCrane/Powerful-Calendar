import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from validate_core_freeze_acceptance_ledger import has_risk_review_basis


class RiskReviewBasisTest(unittest.TestCase):
    def test_accepts_a_valid_current_iso_date(self):
        self.assertTrue(has_risk_review_basis("Risk review 2026-10-25: P1 rationale"))

    def test_rejects_missing_marker_separator_or_invalid_date(self):
        self.assertFalse(has_risk_review_basis("P1 rationale"))
        self.assertFalse(has_risk_review_basis("Risk review 2026-10-25 P1 rationale"))
        self.assertFalse(has_risk_review_basis("Risk review 2026-13-25: P1 rationale"))
        self.assertFalse(has_risk_review_basis("Risk review 20261025: P1 rationale"))


if __name__ == "__main__":
    unittest.main()
