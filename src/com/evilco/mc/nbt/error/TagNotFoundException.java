package com.evilco.mc.nbt.error;

import java.io.IOException;

/**
 * An exception that is thrown when trying to access a component of a CompoundTag that does not exist
 */
public class TagNotFoundException extends IOException {
	private static final long serialVersionUID = -4631008535746749103L;

	/**
	 * Constructs a TagNotFoundException with a default message
	 */
	public TagNotFoundException() {
		super("The tag does not exist");
	}

	/**
	 * Constructs a TagNotFoundException with the given message
	 * @param message the exception message
	 */
	public TagNotFoundException(String message) {
		super(message);
	}

	/**
	 * Constructs a TagNotFoundException with a cause
	 * @param cause the cause exception
	 */
	public TagNotFoundException(Throwable cause) {
		super(cause);
	}

	/**
	 * Constructs a TagNotFoundException with the given message and a cause
	 * @param message the exception message
	 * @param cause the cause exception
	 */
	public TagNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}
}
