package net.skds.wpo.util.interfaces;

public interface IBaseWL {
	default boolean isWL() {
		return true;
	}

	default void fixDS() {
	}
}