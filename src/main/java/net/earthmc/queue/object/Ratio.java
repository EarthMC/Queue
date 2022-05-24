package net.earthmc.queue.object;

import net.earthmc.queue.SubQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class Ratio<T> {
    private final Predicate<T> allPredicate = (t) -> true;
    private final List<Option<T>> options = new ArrayList<>();
    private int optionIndex = 0;

    public Ratio(Map<T, Integer> ratios) {
        for (Map.Entry<T, Integer> entry : ratios.entrySet()) {
            this.options.add(new Option<>(entry.getKey(), entry.getValue()));
        }
    }

    public Ratio(List<SubQueue> subQueues) {
        for (SubQueue subQueue : subQueues) {
            this.options.add(new Option<>((T) subQueue, subQueue.maxSends));
        }
    }

    public T next(boolean dry) {
        return next(dry, allPredicate, null);
    }

    public T next(boolean dry, Predicate<T> predicate, T defaultValue) {
        if (this.options.isEmpty())
            throw new IllegalStateException("Attempted to find next with no options.");

        // Return the current option if there is only 1 value.
        if (this.options.size() == 1)
            return options.get(optionIndex).value;

        final List<Option<T>> predicateMatches = new ArrayList<>(0);

        // Loop through the entire options list once, starting at the last index.
        for (int i = 0; i < options.size(); i++) {

            Option<T> option = options.get(optionIndex);

            boolean predicateMatch = predicate.test(option.value);
            if (predicateMatch)
                predicateMatches.add(option);

            if (option.uses >= option.maxUses || !predicateMatch) {
                // The last options value is more than the max value, reset it to 0
                if (!dry && option.uses >= option.maxUses)
                    option.uses = 0;

                // Set the option index to the next index and continue the loop.
                optionIndex = nextIndex(optionIndex);
            } else {
                // We found a match, this option is under the max uses and matches the predicate.
                // Increment the uses if not dry
                if (!dry)
                    option.uses++;

                return option.value;
            }
        }

        // None of the options had enough uses or matched the predicate,
        // return the first matching predicate (if any). Otherwise, just return the default value.
        // This fixes edge cases where players can get stuck in a sub queue if their sub queue has reached max uses
        // and none of the other sub queues have any players in them. Unit tested at 'testRatioWithMatchingPredicate'
        if (predicateMatches.size() > 0)
            return predicateMatches.get(0).value;
        else
            return defaultValue;
    }

    private int nextIndex(int currentIndex) {
        // Roll around back to 0 if we've reached the last index.
        if (currentIndex + 1 >= options.size())
            return 0;

        return currentIndex + 1;
    }

    private static class Option<T> {
        private final T value;
        private final int maxUses;
        private int uses;

        public Option(T value, int maxUses) {
            this.value = value;
            this.maxUses = maxUses;
            this.uses = 0;
        }
    }
}
