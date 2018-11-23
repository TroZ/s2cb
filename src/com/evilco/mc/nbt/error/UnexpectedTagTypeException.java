package com.evilco.mc.nbt.error;

import java.io.IOException;

/**
 * An exception that is thrown when an entry of a TagCompound or TagList
 * is not of the expected tag type
 */
public class UnexpectedTagTypeException extends IOException {
	private static final long serialVersionUID = -6604963428978583800L;

	/**
	 * Constructs an UnexpectedTagTypeException with a default message
	 */
	public UnexpectedTagTypeException() {
		super("The tag is not of the expected type");
	}

	/**
	 * Constructs an UnexpectedTagTypeException with the given message
	 * @param message the exception message
	 */
	public UnexpectedTagTypeException(String message) {
		super(message);
	}

	/**
	 * Constructs an UnexpectedTagTypeException with a cause
	 * @param cause the cause exception
	 */
	public UnexpectedTagTypeException(Throwable cause) {
		super(cause);
	}

	/**
	 * Constructs an UnexpectedTagTypeException with the given message and a cause
	 * @param message the exception message
	 * @param cause the cause exception
	 */
	public UnexpectedTagTypeException(String message, Throwable cause) {
		super(message, cause);
	}
}
