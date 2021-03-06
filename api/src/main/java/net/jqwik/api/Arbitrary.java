package net.jqwik.api;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.apiguardian.api.*;

import net.jqwik.api.arbitraries.*;

import static org.apiguardian.api.API.Status.*;

/**
 * The main interface for representing objects that can be generated and shrunk.
 *
 * @param <T>
 *            The type of generated objects. Primitive objects (e.g. int, boolean etc.) are represented by their boxed
 *            type (e.g. Integer, Boolean).
 */
@API(status = STABLE, since = "1.0")
public interface Arbitrary<T> {

	@API(status = INTERNAL)
	abstract class ArbitraryFacade {
		private static ArbitraryFacade implementation;

		static {
			implementation = FacadeLoader.load(ArbitraryFacade.class);
		}

		public abstract <T, U> Optional<ExhaustiveGenerator<U>> flatMapExhaustiveGenerator(
			ExhaustiveGenerator<T> self,
			Function<T, Arbitrary<U>> mapper
		);

		public abstract <T> SizableArbitrary<List<T>> list(Arbitrary<T> elementArbitrary);

		public abstract <T> SizableArbitrary<Set<T>> set(Arbitrary<T> elementArbitrary);

		public abstract <T> SizableArbitrary<Stream<T>> stream(Arbitrary<T> elementArbitrary);

		public abstract <T> SizableArbitrary<Iterator<T>> iterator(Arbitrary<T> elementArbitrary);

		public abstract <T, A> SizableArbitrary<A> array(Arbitrary<T> elementArbitrary, Class<A> arrayClass);
	}

	/**
	 * Create the random generator for an arbitrary
	 *
	 * @param genSize a very unspecific configuration parameter that can be used
	 *                to influence the configuration and behaviour of a random generator
	 *                if and only if the generator wants to be influenced.
	 *                Many generators are independent of genSize.
	 *
	 *                The default value of {@code genSize} is the number of tries configured
	 *                for a property. Use {@linkplain Arbitrary#fixGenSize(int)} to fix
	 *                the parameter for a given arbitrary.
	 *
	 * @return a new random generator instance
	 */
	RandomGenerator<T> generator(int genSize);

	/**
	 * Create the exhaustive generator for an arbitrary
	 *
	 * @return a new exhaustive generator or Optional.empty() if it cannot be created.
	 */
	default Optional<ExhaustiveGenerator<T>> exhaustive() {
		return Optional.empty();
	}

	/**
	 * Create optional stream of all possible values this arbitrary could generate.
	 * This is only possible if the arbitrary is available for exhaustive generation.
	 *
	 * @return optional stream of all possible values
	 */
	default Optional<Stream<T>> allValues() {
		return exhaustive().map(generator -> StreamSupport.stream(generator.spliterator(), false));
	}

	/**
	 * Iterate through each value this arbitrary can generate if - and only if -
	 * exhaustive generation is possible. This method can be used for example
	 * to make assertions about a set of values described by an arbitrary.
	 *
	 * @param action the consumer function to be invoked for each value
	 * @throws AssertionError if exhaustive generation is not possible
	 */
	@API(status = MAINTAINED, since = "1.1.2")
	default void forEachValue(Consumer<? super T> action) {
		if (!allValues().isPresent())
			throw new AssertionError("Cannot generate all values of " + this.toString());
		allValues().ifPresent(
			stream -> stream.forEach(action::accept));
	}

	/**
	 * Create a new arbitrary of the same type {@code T} that creates and shrinks the original arbitrary but only allows
	 * values that are accepted by the {@code filterPredicate}.
	 *
	 * @throws JqwikException if filtering will fail to come up with a value after 10000 tries
	 *
	 */
	default Arbitrary<T> filter(Predicate<T> filterPredicate) {
		return new Arbitrary<T>() {
			@Override
			public RandomGenerator<T> generator(int genSize) {
				return Arbitrary.this.generator(genSize).filter(filterPredicate);
			}

			@Override
			public Optional<ExhaustiveGenerator<T>> exhaustive() {
				return Arbitrary.this.exhaustive()
									 .map(generator -> generator.filter(filterPredicate));
			}

		};
	}

	/**
	 * Create a new arbitrary of type {@code U} that maps the values of the original arbitrary using the {@code mapper}
	 * function.
	 */
	default <U> Arbitrary<U> map(Function<T, U> mapper) {
		return new Arbitrary<U>() {
			@Override
			public RandomGenerator<U> generator(int genSize) {
				return Arbitrary.this.generator(genSize).map(mapper);
			}

			@Override
			public Optional<ExhaustiveGenerator<U>> exhaustive() {
				return Arbitrary.this.exhaustive()
									 .map(generator -> generator.map(mapper));
			}
		};
	}

	/**
	 * Create a new arbitrary of type {@code U} that uses the values of the existing arbitrary to create a new arbitrary
	 * using the {@code mapper} function.
	 */
	default <U> Arbitrary<U> flatMap(Function<T, Arbitrary<U>> mapper) {
		return new Arbitrary<U>() {
			@Override
			public RandomGenerator<U> generator(int genSize) {
				return Arbitrary.this.generator(genSize).flatMap(mapper, genSize);
			}

			@Override
			public Optional<ExhaustiveGenerator<U>> exhaustive() {
				return Arbitrary.this.exhaustive()
									 .flatMap(generator -> ArbitraryFacade.implementation.flatMapExhaustiveGenerator(generator, mapper));
			}
		};
	}

	/**
	 * Create a new arbitrary of the same type but inject null values with a probability of {@code nullProbability}.
	 */
	default Arbitrary<T> injectNull(double nullProbability) {
		if (nullProbability <= 0.0) {
			return this;
		}
		return new Arbitrary<T>() {
			@Override
			public RandomGenerator<T> generator(int genSize) {
				return Arbitrary.this.generator(genSize).injectNull(nullProbability);
			}

			@Override
			public Optional<ExhaustiveGenerator<T>> exhaustive() {
				return Arbitrary.this.exhaustive().map(ExhaustiveGenerator::injectNull);
			}
		};
	}

	/**
	 * Create a new arbitrary of the same type {@code T} that creates and shrinks the original arbitrary but will
	 * never generate the same value twice.
	 *
	 * @throws JqwikException if filtering will fail to come up with a value after 10000 tries
	 */
	default Arbitrary<T> unique() {
		return new Arbitrary<T>() {
			@Override
			public RandomGenerator<T> generator(int genSize) {
				return Arbitrary.this.generator(genSize).unique();
			}

			@Override
			public Optional<ExhaustiveGenerator<T>> exhaustive() {
				return Arbitrary.this.exhaustive().map(ExhaustiveGenerator::unique);
			}
		};
	}

	/**
	 * Create a new arbitrary of the same type but inject values in {@code samples} first before continuing with standard
	 * value generation.
	 */
	@SuppressWarnings("unchecked")
	default Arbitrary<T> withSamples(T... samples) {
		return new Arbitrary<T>() {
			@Override
			public RandomGenerator<T> generator(int genSize) {
				return Arbitrary.this.generator(genSize).withSamples(samples);
			}

			@Override
			public Optional<ExhaustiveGenerator<T>> exhaustive() {
				return Arbitrary.this.exhaustive().map(exhaustive -> exhaustive.withSamples(samples));
			}
		};
	}

	/**
	 * Fix the genSize of an arbitrary so that it can no longer be influenced from outside
	 */
	@API(status = EXPERIMENTAL, since = "1.0")
	default Arbitrary<T> fixGenSize(int genSize) {
		return new Arbitrary<T>() {
			@Override
			public RandomGenerator<T> generator(int ignoredGenSize) {
				return Arbitrary.this.generator(genSize);
			}

			@Override
			public Optional<ExhaustiveGenerator<T>> exhaustive() {
				return Arbitrary.this.exhaustive();
			}
		};
	}

	/**
	 * Create a new arbitrary of type {@code List<T>} using the existing arbitrary for generating the elements of the list.
	 */
	default SizableArbitrary<List<T>> list() {
		return ArbitraryFacade.implementation.list(this);
	}

	/**
	 * Create a new arbitrary of type {@code Set<T>} using the existing arbitrary for generating the elements of the set.
	 */
	default SizableArbitrary<Set<T>> set() {
		return ArbitraryFacade.implementation.set(this);
	}

	/**
	 * Create a new arbitrary of type {@code Stream<T>} using the existing arbitrary for generating the elements of the
	 * stream.
	 */
	default SizableArbitrary<Stream<T>> stream() {
		return ArbitraryFacade.implementation.stream(this);
	}

	/**
	 * Create a new arbitrary of type {@code Iterable<T>} using the existing arbitrary for generating the elements of the
	 * stream.
	 */
	default SizableArbitrary<Iterator<T>> iterator() {
		return ArbitraryFacade.implementation.iterator(this);
	}

	/**
	 * Create a new arbitrary of type {@code T[]} using the existing arbitrary for generating the elements of the array.
	 *
	 * @param arrayClass
	 *            The arrays class to create, e.g. {@code String[].class}. This is required due to limitations in Java's
	 *            reflection capabilities.
	 */
	default <A> SizableArbitrary<A> array(Class<A> arrayClass) {
		return ArbitraryFacade.implementation.array(this, arrayClass);
	}

	/**
	 * Create a new arbitrary of type {@code Optional<T>} using the existing arbitrary for generating the elements of the
	 * stream.
	 *
	 * The new arbitrary also generates {@code Optional.empty()} values with a probability of {@code 0.05} (i.e. 1 in 20).
	 */
	default Arbitrary<Optional<T>> optional() {
		return this.injectNull(0.05).map(Optional::ofNullable);
	}

}
