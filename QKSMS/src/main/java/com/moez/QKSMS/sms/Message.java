/*
 * Copyright (C) 2015 QK Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.moez.QKSMS.sms;

/**
 * Class to hold all relevant message information to send
 */
public class Message {

    private String text;
    private String[] addresses;
    private boolean save;
    private int delay;

    /**
     * Constructor
     *
     * @param text      is the message to send
     * @param addresses is an array of phone numbers to send to
     */
    public Message(String text, String[] addresses) {
        this.text = text;
        this.addresses = addresses;
        this.save = true;
        this.delay = 0;
    }

    /**
     * Gets the text of the message to send
     *
     * @return the string of the message to send
     */
    public String getText() {
        return this.text;
    }

    /**
     * Gets the addresses of the message
     *
     * @return an array of strings with all of the addresses
     */
    public String[] getAddresses() {
        return this.addresses;
    }

    /**
     * Gets whether or not to save the message to the database
     *
     * @return a boolean of whether or not to save
     */
    boolean getSave() {
        return this.save;
    }

    /**
     * Gets the time to delay before sending the message
     *
     * @return the delay time in milliseconds
     */
    int getDelay() {
        return this.delay;
    }

}
