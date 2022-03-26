package net.earthmc.queue.object;

import net.earthmc.queue.SubQueue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;

public class Ratio<T> {
    private final List<Option<T>> options = new ArrayList<>();
    private int optionIndex = 0;

    public Ratio(Map<T, Integer> ratios) {
        for (Map.Entry<T, Integer> entry : ratios.entrySet()) {
            this.options.add(new Option<>(entry.getKey(), entry.getValue()));
        }
    }

    public Ratio(Set<SubQueue> subQueues) {
        for (SubQueue subQueue : subQueues) {
            this.options.add(new Option<>((T) subQueue, subQueue.maxSends));
        }
    }

    public T next(boolean dry) {
        return next(dry, (t) -> true, null);
    }

    public T next(boolean dry, Predicate<T> predicate, T defaultValue) {
        if (this.options.isEmpty())
            throw new IllegalStateException("Attempted to find next with no options.");

        // Return the current option if there is only 1 value.
        if (this.options.size() == 1)
            return options.get(optionIndex).value;

        // Loop through the entire options list once, starting at the last index.
        for (int i = 0; i < options.size(); i++) {

            Option<T> option = options.get(optionIndex);

            if (option.uses >= option.maxUses || !predicate.test(option.value)) {
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

        // We didn't find any match, return the default value.
        return defaultValue;
    }

    private int nextIndex(int currentIndex) {
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
