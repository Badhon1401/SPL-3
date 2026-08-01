export const CombinedRatingCalculator = {
  tier: (r: number) => {
    if (r < 1000) return "Beginner";
    if (r < 1200) return "Pupil";
    if (r < 1400) return "Apprentice";
    if (r < 1600) return "Specialist";
    if (r < 1900) return "Expert";
    if (r < 2100) return "Candidate Master";
    if (r < 2400) return "Master";
    if (r < 2600) return "International Master";
    if (r < 3000) return "Grandmaster";
    return "Legendary Grandmaster";
  },
};