/*
 * Copyright 2015-2019 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.api;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.hooks.IEventManager;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.managers.DirectAudioController;
import net.dv8tion.jda.api.managers.Presence;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.GuildAction;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.cache.CacheView;
import net.dv8tion.jda.api.utils.cache.SnowflakeCacheView;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import net.dv8tion.jda.internal.requests.Route;
import okhttp3.OkHttpClient;

import javax.annotation.CheckReturnValue;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The core of JDA. Acts as a registry system of JDA. All parts of the the API can be accessed starting from this class.
 */
public interface JDA
{
    /**
     * Represents the connection status of JDA and its Main WebSocket.
     */
    enum Status
    {
        /**JDA is currently setting up supporting systems like the AudioSystem.*/
        INITIALIZING(true),
        /**JDA has finished setting up supporting systems and is ready to log in.*/
        INITIALIZED(true),
        /**JDA is currently attempting to log in.*/
        LOGGING_IN(true),
        /**JDA is currently attempting to connect it's websocket to Discord.*/
        CONNECTING_TO_WEBSOCKET(true),
        /**JDA has successfully connected it's websocket to Discord and is sending authentication*/
        IDENTIFYING_SESSION(true),
        /**JDA has sent authentication to discord and is awaiting confirmation*/
        AWAITING_LOGIN_CONFIRMATION(true),
        /**JDA is populating internal objects.
         * This process often takes the longest of all Statuses (besides CONNECTED)*/
        LOADING_SUBSYSTEMS(true),
        /**JDA has finished loading everything, is receiving information from Discord and is firing events.*/
        CONNECTED(true),
        /**JDA's main websocket has been disconnected. This <b>DOES NOT</b> mean JDA has shutdown permanently.
         * This is an in-between status. Most likely ATTEMPTING_TO_RECONNECT or SHUTTING_DOWN/SHUTDOWN will soon follow.*/
        DISCONNECTED,
        /** JDA session has been added to {@link net.dv8tion.jda.api.utils.SessionController SessionController}
         * and is awaiting to be dequeued for reconnecting.*/
        RECONNECT_QUEUED,
        /**When trying to reconnect to Discord JDA encountered an issue, most likely related to a lack of internet connection,
         * and is waiting to try reconnecting again.*/
        WAITING_TO_RECONNECT,
        /**JDA has been disconnected from Discord and is currently trying to reestablish the connection.*/
        ATTEMPTING_TO_RECONNECT,
        /**JDA has received a shutdown request or has been disconnected from Discord and reconnect is disabled, thus,
         * JDA is in the process of shutting down*/
        SHUTTING_DOWN,
        /**JDA has finished shutting down and this instance can no longer be used to communicate with the Discord servers.*/
        SHUTDOWN,
        /**While attempting to authenticate, Discord reported that the provided authentication information was invalid.*/
        FAILED_TO_LOGIN;

        private final boolean isInit;

        Status(boolean isInit)
        {
            this.isInit = isInit;
        }

        Status()
        {
            this.isInit = false;
        }

        public boolean isInit()
        {
            return isInit;
        }
    }

    /**
     * Represents the information used to create this shard.
     */
    class ShardInfo
    {
        int shardId;
        int shardTotal;

        public ShardInfo(int shardId, int shardTotal)
        {
            this.shardId = shardId;
            this.shardTotal = shardTotal;
        }

        /**
         * Represents the id of the shard of the current instance.
         * <br>This value will be between 0 and ({@link #getShardTotal()} - 1).
         *
         * @return The id of the currently logged in shard.
         */
        public int getShardId()
        {
            return shardId;
        }

        /**
         * The total amount of shards based on the value provided during JDA instance creation using
         * {@link JDABuilder#useSharding(int, int)}.
         * <br>This <b>does not</b> query Discord to determine the total number of shards.
         * <br>This <b>does not</b> represent the amount of logged in shards.
         * <br>It strictly represents the integer value provided to discord
         * representing the total amount of shards that the developer indicated that it was going to use when
         * initially starting JDA.
         *
         * @return The total of shards based on the total provided by the developer during JDA initialization.
         */
        public int getShardTotal()
        {
            return shardTotal;
        }

        /**
         * Provides a shortcut method for easily printing shard info.
         * <br>Format: "[# / #]"
         * <br>Where the first # is shardId and the second # is shardTotal.
         *
         * @return A String representing the information used to build this shard.
         */
        public String getShardString()
        {
            return "[" + shardId + " / " + shardTotal + "]";
        }

        @Override
        public String toString()
        {
            return "Shard " + getShardString();
        }

        @Override
        public boolean equals(Object o)
        {
            if (!(o instanceof ShardInfo))
                return false;

            ShardInfo oInfo = (ShardInfo) o;
            return shardId == oInfo.getShardId() && shardTotal == oInfo.getShardTotal();
        }
    }

    /**
     * Gets the current {@link net.dv8tion.jda.api.JDA.Status Status} of the JDA instance.
     *
     * @return Current JDA status.
     */
    Status getStatus();

    /**
     * The time in milliseconds that discord took to respond to our last heartbeat
     * <br>This roughly represents the WebSocket ping of this session
     *
     * <p><b>{@link net.dv8tion.jda.api.requests.RestAction RestAction} request times do not
     * correlate to this value!</b>
     *
     * @return time in milliseconds between heartbeat and the heartbeat ack response
     *
     * @see    #getRestPing() Getting RestAction ping
     */
    long getGatewayPing();

    /**
     * The time in milliseconds that discord took to respond to a REST request.
     * <br>This will request the current user from the API and calculate the time the response took.
     *
     * <h2>Example</h2>
     * <pre><code>
     * jda.getRestPing().queue( (time) {@literal ->}
     *     channel.sendMessageFormat("Ping: %d ms", time).queue()
     * );
     * </code></pre>
     *
     * @return {@link net.dv8tion.jda.api.requests.RestAction RestAction} - Type: long
     *
     * @see    #getGatewayPing()
     */
    default RestAction<Long> getRestPing()
    {
        AtomicLong time = new AtomicLong();
        Route.CompiledRoute route = Route.Self.GET_SELF.compile();
        RestActionImpl<Long> action = new RestActionImpl<>(this, route, (response, request) -> System.currentTimeMillis() - time.get());
        action.setCheck(() -> {
            time.set(System.currentTimeMillis());
            return true;
        });
        return action;
    }

    /**
     * This method will block until JDA has reached the specified connection status.
     *
     * <h2>Login Cycle</h2>
     * <ol>
     *  <li>{@link net.dv8tion.jda.api.JDA.Status#INITIALIZING INITIALIZING}</li>
     *  <li>{@link net.dv8tion.jda.api.JDA.Status#INITIALIZED INITIALIZED}</li>
     *  <li>{@link net.dv8tion.jda.api.JDA.Status#LOGGING_IN LOGGING_IN}</li>
     *  <li>{@link net.dv8tion.jda.api.JDA.Status#CONNECTING_TO_WEBSOCKET CONNECTING_TO_WEBSOCKET}</li>
     *  <li>{@link net.dv8tion.jda.api.JDA.Status#IDENTIFYING_SESSION IDENTIFYING_SESSION}</li>
     *  <li>{@link net.dv8tion.jda.api.JDA.Status#AWAITING_LOGIN_CONFIRMATION AWAITING_LOGIN_CONFIRMATION}</li>
     *  <li>{@link net.dv8tion.jda.api.JDA.Status#LOADING_SUBSYSTEMS LOADING_SUBSYSTEMS}</li>
     *  <li>{@link net.dv8tion.jda.api.JDA.Status#CONNECTED CONNECTED}</li>
     * </ol>
     *
     * @param  status
     *         The init status to wait for, once JDA has reached the specified
     *         stage of the startup cycle this method will return.
     *
     * @throws InterruptedException
     *         If this thread is interrupted while waiting
     * @throws IllegalArgumentException
     *         If the provided status is null or not an init status ({@link Status#isInit()})
     * @throws IllegalStateException
     *         If JDA is shutdown during this wait period
     *
     * @return The current JDA instance, for chaining convenience
     */
    JDA awaitStatus(JDA.Status status) throws InterruptedException;

    /**
     * This method will block until JDA has reached the status {@link Status#CONNECTED}.
     * <br>This status means that JDA finished setting up its internal cache and is ready to be used.
     *
     * @throws InterruptedException
     *         If this thread is interrupted while waiting
     * @throws IllegalStateException
     *         If JDA is shutdown during this wait period
     *
     * @return The current JDA instance, for chaining convenience
     */
    default JDA awaitReady() throws InterruptedException
    {
        return awaitStatus(Status.CONNECTED);
    }

    /**
     * Contains all {@code cf-ray} headers that JDA received in this session.
     * <br>These receive a new value whenever the WebSockedClient reconnects to the gateway.
     *
     * <p>This is useful to monitor cloudflare activity from the Discord Developer perspective.
     * <br>Use this list to report connection issues.
     *
     * @return Immutable list of all cf-ray values for this session
     */
    List<String> getCloudflareRays();

    /**
     * Receives all valid {@code _trace} lines that have been sent to us
     * in this session.
     * <br>These values reset on every reconnect! (not resume)
     *
     * @return List of all websocket traces
     */
    List<String> getWebSocketTrace();

    /**
     * {@link ScheduledExecutorService} used to handle rate-limits for {@link RestAction}
     * executions. This is also used in other parts of JDA related to http requests.
     *
     * @return The {@link ScheduledExecutorService} used for http request handling
     */
    ScheduledExecutorService getRateLimitPool();

    /**
     * {@link ScheduledExecutorService} used to send WebSocket messages to discord.
     * <br>This involves initial setup of guilds as well as keeping the connection alive.
     *
     * @return The {@link ScheduledExecutorService} used for WebSocket transmissions
     */
    ScheduledExecutorService getGatewayPool();

    /**
     * {@link ExecutorService} used to handle {@link RestAction} callbacks
     * and completions.
     * <br>By default this uses the {@link ForkJoinPool#commonPool() CommonPool} of the runtime.
     *
     * @return The {@link ExecutorService} used for callbacks
     */
    ExecutorService getCallbackPool();

    /**
     * The {@link OkHttpClient} used for handling http requests from {@link RestAction RestActions}.
     *
     * @return The http client
     */
    OkHttpClient getHttpClient();

    /**
     * Direct access to audio (dis-)connect requests.
     * <br>This should not be used when normal audio operation is desired.
     *
     * <p>The correct way to open and close an audio connection is through the {@link Guild Guild's}
     * {@link AudioManager}.
     *
     * @return The {@link DirectAudioController} for this JDA instance
     */
    DirectAudioController getDirectAudioController();

    /**
     * Changes the internal EventManager.
     *
     * <p>The default EventManager is {@link net.dv8tion.jda.api.hooks.InterfacedEventManager InterfacedEventListener}.
     * <br>There is also an {@link net.dv8tion.jda.api.hooks.AnnotatedEventManager AnnotatedEventManager} available.
     *
     * @param  manager
     *         The new EventManager to use
     */
    void setEventManager(IEventManager manager);

    /**
     * Adds all provided listeners to the event-listeners that will be used to handle events.
     * This uses the {@link net.dv8tion.jda.api.hooks.InterfacedEventManager InterfacedEventListener} by default.
     * To switch to the {@link net.dv8tion.jda.api.hooks.AnnotatedEventManager AnnotatedEventManager}, use {@link #setEventManager(IEventManager)}.
     *
     * Note: when using the {@link net.dv8tion.jda.api.hooks.InterfacedEventManager InterfacedEventListener} (default),
     * given listener <b>must</b> be instance of {@link net.dv8tion.jda.api.hooks.EventListener EventListener}!
     *
     * @param  listeners
     *         The listener(s) which will react to events.
     *
     * @throws java.lang.IllegalArgumentException
     *         If either listeners or one of it's objects is {@code null}.
     */
    void addEventListener(Object... listeners);

    /**
     * Removes all provided listeners from the event-listeners and no longer uses them to handle events.
     *
     * @param  listeners
     *         The listener(s) to be removed.
     *
     * @throws java.lang.IllegalArgumentException
     *         If either listeners or one of it's objects is {@code null}.
     */
    void removeEventListener(Object... listeners);

    /**
     * Returns an unmodifiable List of Objects that have been registered as EventListeners.
     *
     * @return List of currently registered Objects acting as EventListeners.
     */
    List<Object> getRegisteredListeners();

    /**
     * Constructs a new {@link net.dv8tion.jda.api.entities.Guild Guild} with the specified name
     * <br>Use the returned {@link GuildAction GuildAction} to provide
     * further details and settings for the resulting Guild!
     *
     * <p>This RestAction does not provide the resulting Guild!
     * It will be in a following {@link net.dv8tion.jda.api.events.guild.GuildJoinEvent GuildJoinEvent}.
     *
     * @param  name
     *         The name of the resulting guild
     *
     * @throws java.lang.IllegalStateException
     *         If the currently logged in account is from
     *         <ul>
     *             <li>{@link net.dv8tion.jda.api.AccountType#CLIENT AccountType.CLIENT} and the account is in 100 or more guilds</li>
     *             <li>{@link net.dv8tion.jda.api.AccountType#BOT AccountType.BOT} and the account is in 10 or more guilds</li>
     *         </ul>
     * @throws java.lang.IllegalArgumentException
     *         If the provided name is empty, {@code null} or not between 2-100 characters
     *
     * @return {@link GuildAction GuildAction}
     *         <br>Allows for setting various details for the resulting Guild
     */
    GuildAction createGuild(String name);

    /**
     * {@link net.dv8tion.jda.api.utils.cache.CacheView CacheView} of
     * all cached {@link net.dv8tion.jda.api.managers.AudioManager AudioManagers} created for this JDA instance.
     * <br>AudioManagers are created when first retrieved via {@link net.dv8tion.jda.api.entities.Guild#getAudioManager() Guild.getAudioManager()}.
     * <u>Using this will perform better than calling {@code Guild.getAudioManager()} iteratively as that would cause many useless audio managers to be created!</u>
     *
     * <p>AudioManagers are cross-session persistent!
     *
     * @return {@link net.dv8tion.jda.api.utils.cache.CacheView CacheView}
     */
    CacheView<AudioManager> getAudioManagerCache();

    /**
     * Immutable list of all created {@link net.dv8tion.jda.api.managers.AudioManager AudioManagers} for this JDA instance!
     *
     * @return Immutable list of all created AudioManager instances
     */
    default List<AudioManager> getAudioManagers()
    {
        return getAudioManagerCache().asList();
    }


    /**
     * {@link net.dv8tion.jda.api.utils.cache.SnowflakeCacheView SnowflakeCacheView} of
     * all cached {@link net.dv8tion.jda.api.entities.User Users} visible to this JDA session.
     *
     * @return {@link net.dv8tion.jda.api.utils.cache.SnowflakeCacheView SnowflakeCacheView}
     */
    SnowflakeCacheView<User> getUserCache();

    /**
     * An unmodifiable list of all {@link net.dv8tion.jda.api.entities.User Users} that share a
     * {@link net.dv8tion.jda.api.entities.Guild Guild} with the currently logged in account.
     * <br>This list will never contain duplicates and represents all
     * {@link net.dv8tion.jda.api.entities.User Users} that JDA can currently see.
     *
     * <p>If the developer is sharding, then only users from guilds connected to the specifically logged in
     * shard will be returned in the List.
     *
     * <p>This copies the backing store into a list. This means every call
     * creates a new list with O(n) complexity. It is recommended to store this into
     * a local variable or use {@link #getUserCache()} and use its more efficient
     * versions of handling these values.
     *
     * @return List of all {@link net.dv8tion.jda.api.entities.User Users} that are visible to JDA.
     */
    default List<User> getUsers()
    {
        return getUserCache().asList();
    }

    /**
     * This returns the {@link net.dv8tion.jda.api.entities.User User} which has the same id as the one provided.
     * <br>If there is no visible user with an id that matches the provided one, this returns {@code null}.
     *
     * @param  id
     *         The id of the requested {@link net.dv8tion.jda.api.entities.User User}.
     *
     * @throws java.lang.NumberFormatException
     *         If the provided {@code id} cannot be parsed by {@link Long#parseLong(String)}
     *
     * @return Possibly-null {@link net.dv8tion.jda.api.entities.User User} with matching id.
     */
    default User getUserById(String id)
    {
        return getUserCache().getElementById(id);
    }

    /**
     * This returns the {@link net.dv8tion.jda.api.entities.User User} which has the same id as the one provided.
     * <br>If there is no visible user with an id that matches the provided one, this returns {@code null}.
     *
     * @param  id
     *         The id of the requested {@link net.dv8tion.jda.api.entities.User User}.
     *
     * @return Possibly-null {@link net.dv8tion.jda.api.entities.User User} with matching id.
     */
    default User getUserById(long id)
    {
        return getUserCache().getElementById(id);
    }

    /**
     * This unmodifiable returns all {@link net.dv8tion.jda.api.entities.User Users} that have the same username as the one provided.
     * <br>If there are no {@link net.dv8tion.jda.api.entities.User Users} with the provided name, then this returns an empty list.
     *
     * <p><b>Note: </b> This does **not** consider nicknames, it only considers {@link net.dv8tion.jda.api.entities.User#getName()}
     *
     * @param  name
     *         The name of the requested {@link net.dv8tion.jda.api.entities.User Users}.
     * @param  ignoreCase
     *         Whether to ignore case or not when comparing the provided name to each {@link net.dv8tion.jda.api.entities.User#getName()}.
     *
     * @return Possibly-empty list of {@link net.dv8tion.jda.api.entities.User Users} that all have the same name as the provided name.
     */
    default List<User> getUsersByName(String name, boolean ignoreCase)
    {
        return getUserCache().getElementsByName(name, ignoreCase);
    }

    /**
     * Gets all {@link net.dv8tion.jda.api.entities.Guild Guilds} that contain all given users as their members.
     *
     * @param  users
     *         The users which all the returned {@link net.dv8tion.jda.api.entities.Guild Guilds} must contain.
     *
     * @return Unmodifiable list of all {@link net.dv8tion.jda.api.entities.Guild Guild} instances which have all {@link net.dv8tion.jda.api.entities.User Users} in them.
     */
    List<Guild> getMutualGuilds(User... users);

    /**
     * Gets all {@link net.dv8tion.jda.api.entities.Guild Guilds} that contain all given users as their members.
     *
     * @param users
     *        The users which all the returned {@link net.dv8tion.jda.api.entities.Guild Guilds} must contain.
     *
     * @return Unmodifiable list of all {@link net.dv8tion.jda.api.entities.Guild Guild} instances which have all {@link net.dv8tion.jda.api.entities.User Users} in them.
     */
    List<Guild> getMutualGuilds(Collection<User> users);

    /**
     * Attempts to retrieve a {@link net.dv8tion.jda.api.entities.User User} object based on the provided id.
     * <br>This first calls {@link #getUserById(long)}, and if the return is {@code null} then a request
     * is made to the Discord servers.
     *
     * <p>The returned {@link net.dv8tion.jda.api.requests.RestAction RestAction} can encounter the following Discord errors:
     * <ul>
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#UNKNOWN_USER ErrorResponse.UNKNOWN_USER}
     *     <br>Occurs when the provided id does not refer to a {@link net.dv8tion.jda.api.entities.User User}
     *     known by Discord. Typically occurs when developers provide an incomplete id (cut short).</li>
     * </ul>
     *
     * @param  id
     *         The id of the requested {@link net.dv8tion.jda.api.entities.User User}.
     *
     * @throws net.dv8tion.jda.api.exceptions.AccountTypeException
     *         This endpoint is {@link AccountType#BOT} only.
     *
     * @throws java.lang.NumberFormatException
     *         If the provided {@code id} cannot be parsed by {@link Long#parseLong(String)}
     * @throws java.lang.IllegalArgumentException
     *         <ul>
     *             <li>If the provided id String is null.</li>
     *             <li>If the provided id String is empty.</li>
     *         </ul>
     *
     * @return {@link net.dv8tion.jda.api.requests.RestAction RestAction} - Type: {@link net.dv8tion.jda.api.entities.User User}
     *         <br>On request, gets the User with id matching provided id from Discord.
     */
    @CheckReturnValue
    RestAction<User> retrieveUserById(String id);

    /**
     * Attempts to retrieve a {@link net.dv8tion.jda.api.entities.User User} object based on the provided id.
     * <br>This first calls {@link #getUserById(long)}, and if the return is {@code null} then a request
     * is made to the Discord servers.
     *
     * <p>The returned {@link net.dv8tion.jda.api.requests.RestAction RestAction} can encounter the following Discord errors:
     * <ul>
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#UNKNOWN_USER ErrorResponse.UNKNOWN_USER}
     *     <br>Occurs when the provided id does not refer to a {@link net.dv8tion.jda.api.entities.User User}
     *     known by Discord. Typically occurs when developers provide an incomplete id (cut short).</li>
     * </ul>
     *
     * @param  id
     *         The id of the requested {@link net.dv8tion.jda.api.entities.User User}.
     *
     * @throws net.dv8tion.jda.api.exceptions.AccountTypeException
     *         This endpoint is {@link AccountType#BOT} only.
     *
     * @return {@link net.dv8tion.jda.api.requests.RestAction RestAction} - Type: {@link net.dv8tion.jda.api.entities.User User}
     *         <br>On request, gets the User with id matching provided id from Discord.
     */
    @CheckReturnValue
    RestAction<User> retrieveUserById(long id);

    /**
     * {@link net.dv8tion.jda.api.utils.cache.SnowflakeCacheView SnowflakeCacheView} of
     * all cached {@link net.dv8tion.jda.api.entities.Guild Guilds} visible to this JDA session.
     *
     * @return {@link net.dv8tion.jda.api.utils.cache.SnowflakeCacheView SnowflakeCacheView}
     */
    SnowflakeCacheView<Guild> getGuildCache();

    /**
     * An unmodifiable List of all {@link net.dv8tion.jda.api.entities.Guild Guilds} that the logged account is connected to.
     * <br>If this account is not connected to any {@link net.dv8tion.jda.api.entities.Guild Guilds}, this will return an empty list.
     *
     * <p>If the developer is sharding ({@link net.dv8tion.jda.api.JDABuilder#useSharding(int, int)}, then this list
     * will only contain the {@link net.dv8tion.jda.api.entities.Guild Guilds} that the shard is actually connected to.
     * Discord determines which guilds a shard is connect to using the following format:
     * <br>Guild connected if shardId == (guildId {@literal >>} 22) % totalShards;
     * <br>Source for formula: <a href="https://discordapp.com/developers/docs/topics/gateway#sharding">Discord Documentation</a>
     *
     * <p>This copies the backing store into a list. This means every call
     * creates a new list with O(n) complexity. It is recommended to store this into
     * a local variable or use {@link #getGuildCache()} and use its more efficient
     * versions of handling these values.
     *
     * @return Possibly-empty list of all the {@link net.dv8tion.jda.api.entities.Guild Guilds} that this account is connected to.
     */
    default List<Guild> getGuilds()
    {
        return getGuildCache().asList();
    }

    /**
     * This returns the {@link net.dv8tion.jda.api.entities.Guild Guild} which has the same id as the one provided.
     * <br>If there is no connected guild with an id that matches the provided one, then this returns {@code null}.
     *
     * @param  id
     *         The id of the {@link net.dv8tion.jda.api.entities.Guild Guild}.
     *
     * @throws java.lang.NumberFormatException
     *         If the provided {@code id} cannot be parsed by {@link Long#parseLong(String)}
     *
     * @return Possibly-null {@link net.dv8tion.jda.api.entities.Guild Guild} with matching id.
     */
    default Guild getGuildById(String id)
    {
        return getGuildCache().getElementById(id);
    }

    /**
     * This returns the {@link net.dv8tion.jda.api.entities.Guild Guild} which has the same id as the one provided.
     * <br>If there is no connected guild with an id that matches the provided one, then this returns {@code null}.
     *
     * @param  id
     *         The id of the {@link net.dv8tion.jda.api.entities.Guild Guild}.
     *
     * @return Possibly-null {@link net.dv8tion.jda.api.entities.Guild Guild} with matching id.
     */
    default Guild getGuildById(long id)
    {
        return getGuildCache().getElementById(id);
    }

    /**
     * An unmodifiable list of all {@link net.dv8tion.jda.api.entities.Guild Guilds} that have the same name as the one provided.
     * <br>If there are no {@link net.dv8tion.jda.api.entities.Guild Guilds} with the provided name, then this returns an empty list.
     *
     * @param  name
     *         The name of the requested {@link net.dv8tion.jda.api.entities.Guild Guilds}.
     * @param  ignoreCase
     *         Whether to ignore case or not when comparing the provided name to each {@link net.dv8tion.jda.api.entities.Guild#getName()}.
     *
     * @return Possibly-empty list of all the {@link net.dv8tion.jda.api.entities.Guild Guilds} that all have the same name as the provided name.
     */
    default List<Guild> getGuildsByName(String name, boolean ignoreCase)
    {
        return getGuildCache().getElementsByName(name, ignoreCase);
    }

    /**
     * Unified {@link net.dv8tion.jda.api.utils.cache.SnowflakeCacheView SnowflakeCacheView} of
     * all cached {@link net.dv8tion.jda.api.entities.Role Roles} visible to this JDA session.
     *
     * @return Unified {@link net.dv8tion.jda.api.utils.cache.SnowflakeCacheView SnowflakeCacheView}
     *
     * @see    net.dv8tion.jda.api.utils.cache.CacheView#allSnowflakes(java.util.function.Supplier) CacheView.allSnowflakes(...)
     */
    SnowflakeCacheView<Role> getRoleCache();

    /**
     * All {@link net.dv8tion.jda.api.entities.Role Roles} this JDA instance can see. <br>This will iterate over each
     * {@link net.dv8tion.jda.api.entities.Guild Guild} retrieved from {@link #getGuilds()} and collect its {@link
     * net.dv8tion.jda.api.entities.Guild#getRoles() Guild.getRoles()}.
     *
     * <p>This copies the backing store into a list. This means every call
     * creates a new list with O(n) complexity. It is recommended to store this into
     * a local variable or use {@link #getRoleCache()} and use its more efficient
     * versions of handling these values.
     *
     * @return Immutable List of all visible Roles
     */
    default List<Role> getRoles()
    {
        return getRoleCache().asList();
    }

    /**
     * Retrieves the {@link net.dv8tion.jda.api.entities.Role Role} associated to the provided id. <br>This iterates
     * over all {@link net.dv8tion.jda.api.entities.Guild Guilds} and check whether a Role from that Guild is assigned
     * to the specified ID and will return the first that can be found.
     *
     * @param id
     *         The id of the searched Role
     * @return Possibly-null {@link net.dv8tion.jda.api.entities.Role Role} for the specified ID
     * @throws java.lang.NumberFormatException
     *         If the provided {@code id} cannot be parsed by {@link Long#parseLong(String)}
     */
    default Role getRoleById(String id)
    {
        return getRoleCache().getElementById(id);
    }

    /**
     * Retrieves the {@link net.dv8tion.jda.api.entities.Role Role} associated to the provided id. <br>This iterates
     * over all {@link net.dv8tion.jda.api.entities.Guild Guilds} and check whether a Role from that Guild is assigned
     * to the specified ID and will return the first that can be found.
     *
     * @param id
     *         The id of the searched Role
     * @return Possibly-null {@link net.dv8tion.jda.api.entities.Role Role} for the specified ID
     */
    default Role getRoleById(long id)
    {
        return getRoleCache().getElementById(id);
    }

    /**
     * Retrieves all {@link net.dv8tion.jda.api.entities.Role Roles} visible to this JDA instance.
     * <br>This simply filters the Roles returned by {@link #getRoles()} with the provided name, either using
     * {@link String#equals(Object)} or {@link String#equalsIgnoreCase(String)} on {@link net.dv8tion.jda.api.entities.Role#getName()}.
     *
     * @param  name
     *         The name for the Roles
     * @param  ignoreCase
     *         Whether to use {@link String#equalsIgnoreCase(String)}
     * @return Immutable List of all Roles matching the parameters provided.
     */
    default List<Role> getRolesByName(String name, boolean ignoreCase)
    {
        return getRoleCache().getElementsByName(name, ignoreCase);
    }

    /**
     * {@link net.dv8tion.jda.api.utils.cache.SnowflakeCacheView SnowflakeCacheView} of
     * all cached {@link net.dv8tion.jda.api.entities.Category Categories} visible to this JDA session.
     *
     * @return {@link net.dv8tion.jda.api.utils.cache.SnowflakeCacheView SnowflakeCacheView}
     */
    SnowflakeCacheView<Category> getCategoryCache();

    /**
     * Gets the {@link net.dv8tion.jda.api.entities.Category Category} that matches the provided id. <br>If there is no
     * matching {@link net.dv8tion.jda.api.entities.Category Category} this returns {@code null}.
     *
     * @param id
     *         The snowflake ID of the wanted Category
     * @return Possibly-null {@link net.dv8tion.jda.api.entities.Category Category} for the provided ID.
     * @throws java.lang.IllegalArgumentException
     *         If the provided ID is not a valid {@code long}
     */
    default Category getCategoryById(String id)
    {
        return getCategoryCache().getElementById(id);
    }

    /**
     * Gets the {@link net.dv8tion.jda.api.entities.Category Category} that matches the provided id. <br>If there is no
     * matching {@link net.dv8tion.jda.api.entities.Category Category} this returns {@code null}.
     *
     * @param id
     *         The snowflake ID of the wanted Category
     * @return Possibly-null {@link net.dv8tion.jda.api.entities.Category Category} for the provided ID.
     */
    default Category getCategoryById(long id)
    {
        return getCategoryCache().getElementById(id);
    }

    /**
     * Gets all {@link net.dv8tion.jda.api.entities.Category Categories} visible to the currently logged in account.
     *
     * <p>This copies the backing store into a list. This means every call
     * creates a new list with O(n) complexity. It is recommended to store this into
     * a local variable or use {@link #getCategoryCache()} and use its more efficient
     * versions of handling these values.
     *
     * @return An immutable list of all visible {@link net.dv8tion.jda.api.entities.Category Categories}.
     */
    default List<Category> getCategories()
    {
        return getCategoryCache().asList();
    }

    /**
     * Gets a list of all {@link net.dv8tion.jda.api.entities.Category Categories} that have the same name as the one
     * provided. <br>If there are no matching categories this will return an empty list.
     *
     * @param name
     *         The name to check
     * @param ignoreCase
     *         Whether to ignore case on name checking
     * @return Immutable list of all categories matching the provided name
     * @throws java.lang.IllegalArgumentException
     *         If the provided name is {@code null}
     */
    default List<Category> getCategoriesByName(String name, boolean ignoreCase)
    {
        return getCategoryCache().getElementsByName(name, ignoreCase);
    }

    /**
     * {@link net.dv8tion.jda.api.utils.cache.SnowflakeCacheView SnowflakeCacheView} of
     * all cached {@link net.dv8tion.jda.api.entities.TextChannel TextChannels} visible to this JDA session.
     *
     * @return {@link net.dv8tion.jda.api.utils.cache.SnowflakeCacheView SnowflakeCacheView}
     */
    SnowflakeCacheView<TextChannel> getTextChannelCache();

    /**
     * An unmodifiable List of all {@link net.dv8tion.jda.api.entities.TextChannel TextChannels} of all connected
     * {@link net.dv8tion.jda.api.entities.Guild Guilds}.
     *
     * <p><b>Note:</b> just because a {@link net.dv8tion.jda.api.entities.TextChannel TextChannel} is present in this list does
     * not mean that you will be able to send messages to it. Furthermore, if you log into this account on the discord
     * client, it is possible that you will see fewer channels than this returns. This is because the discord
     * client hides any {@link net.dv8tion.jda.api.entities.TextChannel TextChannel} that you don't have the
     * {@link net.dv8tion.jda.api.Permission#MESSAGE_READ Permission.MESSAGE_READ} permission in.
     *
     * <p>This copies the backing store into a list. This means every call
     * creates a new list with O(n) complexity. It is recommended to store this into
     * a local variable or use {@link #getTextChannelCache()} and use its more efficient
     * versions of handling these values.
     *
     * @return Possibly-empty list of all known {@link net.dv8tion.jda.api.entities.TextChannel TextChannels}.
     */
    default List<TextChannel> getTextChannels()
    {
        return getTextChannelCache().asList();
    }

    /**
     * This returns the {@link net.dv8tion.jda.api.entities.TextChannel TextChannel} which has the same id as the one provided.
     * <br>If there is no known {@link net.dv8tion.jda.api.entities.TextChannel TextChannel} with an id that matches the
     * provided one, then this returns {@code null}.
     *
     * <p><b>Note:</b> just because a {@link net.dv8tion.jda.api.entities.TextChannel TextChannel} is present does
     * not mean that you will be able to send messages to it. Furthermore, if you log into this account on the discord
     * client, it is you will not see the channel that this returns. This is because the discord client
     * hides any {@link net.dv8tion.jda.api.entities.TextChannel TextChannel} that you don't have the
     * {@link net.dv8tion.jda.api.Permission#MESSAGE_READ Permission.MESSAGE_READ} permission in.
     *
     * @param  id
     *         The id of the {@link net.dv8tion.jda.api.entities.TextChannel TextChannel}.
     * @throws java.lang.NumberFormatException
     *         If the provided {@code id} cannot be parsed by {@link Long#parseLong(String)}
     *
     * @return Possibly-null {@link net.dv8tion.jda.api.entities.TextChannel TextChannel} with matching id.
     */
    default TextChannel getTextChannelById(String id)
    {
        return getTextChannelCache().getElementById(id);
    }

    /**
     * This returns the {@link net.dv8tion.jda.api.entities.TextChannel TextChannel} which has the same id as the one provided.
     * <br>If there is no known {@link net.dv8tion.jda.api.entities.TextChannel TextChannel} with an id that matches the
     * provided one, then this returns {@code null}.
     *
     * <p><b>Note:</b> just because a {@link net.dv8tion.jda.api.entities.TextChannel TextChannel} is present does
     * not mean that you will be able to send messages to it. Furthermore, if you log into this account on the discord
     * client, it is you will not see the channel that this returns. This is because the discord client
     * hides any {@link net.dv8tion.jda.api.entities.TextChannel TextChannel} that you don't have the
     * {@link net.dv8tion.jda.api.Permission#MESSAGE_READ Permission.MESSAGE_READ} permission in.
     *
     * @param  id
     *         The id of the {@link net.dv8tion.jda.api.entities.TextChannel TextChannel}.
     *
     * @return Possibly-null {@link net.dv8tion.jda.api.entities.TextChannel TextChannel} with matching id.
     */
    default TextChannel getTextChannelById(long id)
    {
        return getTextChannelCache().getElementById(id);
    }

    /**
     * An unmodifiable list of all {@link net.dv8tion.jda.api.entities.TextChannel TextChannels} that have the same name as the one provided.
     * <br>If there are no {@link net.dv8tion.jda.api.entities.TextChannel TextChannels} with the provided name, then this returns an empty list.
     *
     * <p><b>Note:</b> just because a {@link net.dv8tion.jda.api.entities.TextChannel TextChannel} is present in this list does
     * not mean that you will be able to send messages to it. Furthermore, if you log into this account on the discord
     * client, it is possible that you will see fewer channels than this returns. This is because the discord client
     * hides any {@link net.dv8tion.jda.api.entities.TextChannel TextChannel} that you don't have the
     * {@link net.dv8tion.jda.api.Permission#MESSAGE_READ Permission.MESSAGE_READ} permission in.
     *
     * @param  name
     *         The name of the requested {@link net.dv8tion.jda.api.entities.TextChannel TextChannels}.
     * @param  ignoreCase
     *         Whether to ignore case or not when comparing the provided name to each {@link net.dv8tion.jda.api.entities.TextChannel#getName()}.
     *
     * @return Possibly-empty list of all the {@link net.dv8tion.jda.api.entities.TextChannel TextChannels} that all have the
     *         same name as the provided name.
     */
    default List<TextChannel> getTextChannelsByName(String name, boolean ignoreCase)
    {
        return getTextChannelCache().getElementsByName(name, ignoreCase);
    }

    /**
     * {@link net.dv8tion.jda.api.utils.cache.SnowflakeCacheView SnowflakeCacheView} of
     * all cached {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannels} visible to this JDA session.
     *
     * @return {@link net.dv8tion.jda.api.utils.cache.SnowflakeCacheView SnowflakeCacheView}
     */
    SnowflakeCacheView<VoiceChannel> getVoiceChannelCache();

    /**
     * An unmodifiable list of all {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannels} of all connected
     * {@link net.dv8tion.jda.api.entities.Guild Guilds}.
     *
     * <p>This copies the backing store into a list. This means every call
     * creates a new list with O(n) complexity. It is recommended to store this into
     * a local variable or use {@link #getVoiceChannelCache()} and use its more efficient
     * versions of handling these values.
     *
     * @return Possible-empty list of all known {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannels}.
     */
    default List<VoiceChannel> getVoiceChannels()
    {
        return getVoiceChannelCache().asList();
    }

    /**
     * This returns the {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannel} which has the same id as the one provided.
     * <br>If there is no known {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannel} with an id that matches the provided
     * one, then this returns {@code null}.
     *
     * @param  id
     *         The id of the {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannel}.
     * @throws java.lang.NumberFormatException
     *         If the provided {@code id} cannot be parsed by {@link Long#parseLong(String)}
     *
     * @return Possibly-null {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannel} with matching id.
     */
    default VoiceChannel getVoiceChannelById(String id)
    {
        return getVoiceChannelCache().getElementById(id);
    }

    /**
     * This returns the {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannel} which has the same id as the one provided.
     * <br>If there is no known {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannel} with an id that matches the provided
     * one, then this returns {@code null}.
     *
     * @param  id
     *         The id of the {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannel}.
     *
     * @return Possibly-null {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannel} with matching id.
     */
    default VoiceChannel getVoiceChannelById(long id)
    {
        return getVoiceChannelCache().getElementById(id);
    }

    /**
     * An unmodifiable list of all {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannels} that have the same name as the one provided.
     * <br>If there are no {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannels} with the provided name, then this returns an empty list.
     *
     * @param  name
     *         The name of the requested {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannels}.
     * @param  ignoreCase
     *         Whether to ignore case or not when comparing the provided name to each {@link net.dv8tion.jda.api.entities.VoiceChannel#getName()}.
     *
     * @return Possibly-empty list of all the {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannels} that all have the
     *         same name as the provided name.
     */
    default List<VoiceChannel> getVoiceChannelByName(String name, boolean ignoreCase)
    {
        return getVoiceChannelCache().getElementsByName(name, ignoreCase);
    }

    /**
     * {@link net.dv8tion.jda.api.utils.cache.SnowflakeCacheView SnowflakeCacheView} of
     * all cached {@link net.dv8tion.jda.api.entities.PrivateChannel PrivateChannels} visible to this JDA session.
     *
     * @return {@link net.dv8tion.jda.api.utils.cache.SnowflakeCacheView SnowflakeCacheView}
     */
    SnowflakeCacheView<PrivateChannel> getPrivateChannelCache();

    /**
     * An unmodifiable list of all known {@link net.dv8tion.jda.api.entities.PrivateChannel PrivateChannels}.
     *
     * <p>This copies the backing store into a list. This means every call
     * creates a new list with O(n) complexity. It is recommended to store this into
     * a local variable or use {@link #getPrivateChannelCache()} and use its more efficient
     * versions of handling these values.
     *
     * @return Possibly-empty list of all {@link net.dv8tion.jda.api.entities.PrivateChannel PrivateChannels}.
     */
    default List<PrivateChannel> getPrivateChannels()
    {
        return getPrivateChannelCache().asList();
    }

    /**
     * This returns the {@link net.dv8tion.jda.api.entities.PrivateChannel PrivateChannel} which has the same id as the one provided.
     * <br>If there is no known {@link net.dv8tion.jda.api.entities.PrivateChannel PrivateChannel} with an id that matches the provided
     * one, then this returns {@code null}.
     *
     * @param  id
     *         The id of the {@link net.dv8tion.jda.api.entities.PrivateChannel PrivateChannel}.
     * @throws java.lang.NumberFormatException
     *         If the provided {@code id} cannot be parsed by {@link Long#parseLong(String)}
     *
     * @return Possibly-null {@link net.dv8tion.jda.api.entities.PrivateChannel PrivateChannel} with matching id.
     */
    default PrivateChannel getPrivateChannelById(String id)
    {
        return getPrivateChannelCache().getElementById(id);
    }

    /**
     * This returns the {@link net.dv8tion.jda.api.entities.PrivateChannel PrivateChannel} which has the same id as the one provided.
     * <br>If there is no known {@link net.dv8tion.jda.api.entities.PrivateChannel PrivateChannel} with an id that matches the provided
     * one, then this returns {@code null}.
     *
     * @param  id
     *         The id of the {@link net.dv8tion.jda.api.entities.PrivateChannel PrivateChannel}.
     *
     * @return Possibly-null {@link net.dv8tion.jda.api.entities.PrivateChannel PrivateChannel} with matching id.
     */
    default PrivateChannel getPrivateChannelById(long id)
    {
        return getPrivateChannelCache().getElementById(id);
    }

    /**
     * Unified {@link net.dv8tion.jda.api.utils.cache.SnowflakeCacheView SnowflakeCacheView} of
     * all cached {@link net.dv8tion.jda.api.entities.Emote Emotes} visible to this JDA session.
     *
     * @return Unified {@link net.dv8tion.jda.api.utils.cache.SnowflakeCacheView SnowflakeCacheView}
     *
     * @see    net.dv8tion.jda.api.utils.cache.CacheView#allSnowflakes(java.util.function.Supplier) CacheView.allSnowflakes(...)
     */
    SnowflakeCacheView<Emote> getEmoteCache();

    /**
     * A collection of all to us known emotes (managed/restricted included).
     * <br>This will be empty if {@link net.dv8tion.jda.api.utils.cache.CacheFlag#EMOTE} is disabled.
     *
     * <p><b>Hint</b>: To check whether you can use an {@link net.dv8tion.jda.api.entities.Emote Emote} in a specific
     * context you can use {@link Emote#canInteract(net.dv8tion.jda.api.entities.Member)} or {@link
     * Emote#canInteract(net.dv8tion.jda.api.entities.User, net.dv8tion.jda.api.entities.MessageChannel)}
     *
     * <p><b>Unicode emojis are not included as {@link net.dv8tion.jda.api.entities.Emote Emote}!</b>
     *
     * <p>This copies the backing store into a list. This means every call
     * creates a new list with O(n) complexity. It is recommended to store this into
     * a local variable or use {@link #getEmoteCache()} and use its more efficient
     * versions of handling these values.
     *
     * @return An immutable list of Emotes (which may or may not be available to usage).
     */
    default List<Emote> getEmotes()
    {
        return getEmoteCache().asList();
    }

    /**
     * Retrieves an emote matching the specified {@code id} if one is available in our cache.
     * <br>This will be null if {@link net.dv8tion.jda.api.utils.cache.CacheFlag#EMOTE} is disabled.
     *
     * <p><b>Unicode emojis are not included as {@link net.dv8tion.jda.api.entities.Emote Emote}!</b>
     *
     * @param id
     *         The id of the requested {@link net.dv8tion.jda.api.entities.Emote}.
     * @return An {@link net.dv8tion.jda.api.entities.Emote Emote} represented by this id or null if none is found in
     *         our cache.
     * @throws java.lang.NumberFormatException
     *         If the provided {@code id} cannot be parsed by {@link Long#parseLong(String)}
     */
    default Emote getEmoteById(String id)
    {
        return getEmoteCache().getElementById(id);
    }

    /**
     * Retrieves an emote matching the specified {@code id} if one is available in our cache.
     * <br>This will be null if {@link net.dv8tion.jda.api.utils.cache.CacheFlag#EMOTE} is disabled.
     *
     * <p><b>Unicode emojis are not included as {@link net.dv8tion.jda.api.entities.Emote Emote}!</b>
     *
     * @param id
     *         The id of the requested {@link net.dv8tion.jda.api.entities.Emote}.
     * @return An {@link net.dv8tion.jda.api.entities.Emote Emote} represented by this id or null if none is found in
     *         our cache.
     */
    default Emote getEmoteById(long id)
    {
        return getEmoteCache().getElementById(id);
    }

    /**
     * An unmodifiable list of all {@link net.dv8tion.jda.api.entities.Emote Emotes} that have the same name as the one
     * provided. <br>If there are no {@link net.dv8tion.jda.api.entities.Emote Emotes} with the provided name, then
     * this returns an empty list.
     * <br>This will be empty if {@link net.dv8tion.jda.api.utils.cache.CacheFlag#EMOTE} is disabled.
     *
     * <p><b>Unicode emojis are not included as {@link net.dv8tion.jda.api.entities.Emote Emote}!</b>
     *
     * @param name
     *         The name of the requested {@link net.dv8tion.jda.api.entities.Emote Emotes}.
     * @param ignoreCase
     *         Whether to ignore case or not when comparing the provided name to each {@link
     *         net.dv8tion.jda.api.entities.Emote#getName()}.
     * @return Possibly-empty list of all the {@link net.dv8tion.jda.api.entities.Emote Emotes} that all have the same
     *         name as the provided name.
     */
    default List<Emote> getEmotesByName(String name, boolean ignoreCase)
    {
        return getEmoteCache().getElementsByName(name, ignoreCase);
    }

    /**
     * The EventManager used by this JDA instance.
     *
     * @return The {@link net.dv8tion.jda.api.hooks.IEventManager}
     */
    IEventManager getEventManager();

    /**
     * Returns the currently logged in account represented by {@link net.dv8tion.jda.api.entities.SelfUser SelfUser}.
     * <br>Account settings <b>cannot</b> be modified using this object. If you wish to modify account settings please
     * use the AccountManager which is accessible by {@link net.dv8tion.jda.api.entities.SelfUser#getManager()}.
     *
     * @return The currently logged in account.
     */
    SelfUser getSelfUser();

    /**
     * The {@link net.dv8tion.jda.api.managers.Presence Presence} controller for the current session.
     * <br>Used to set {@link net.dv8tion.jda.api.entities.Activity} and {@link net.dv8tion.jda.api.OnlineStatus} information.
     *
     * @return The never-null {@link net.dv8tion.jda.api.managers.Presence Presence} for this session.
     */
    Presence getPresence();

    /**
     * The shard information used when creating this instance of JDA.
     * <br>Represents the information provided to {@link net.dv8tion.jda.api.JDABuilder#useSharding(int, int)}.
     *
     * @return The shard information for this shard or {@code null} if this JDA instance isn't sharding.
     */
    ShardInfo getShardInfo();

    /**
     * The login token that is currently being used for Discord authentication.
     *
     * @return Never-null, 18 character length string containing the auth token.
     */
    String getToken();

    /**
     * This value is the total amount of JSON responses that discord has sent.
     * <br>This value resets every time the websocket has to perform a full reconnect (not resume).
     *
     * @return Never-negative long containing total response amount.
     */
    long getResponseTotal();

    /**
     * This value is the maximum amount of time, in seconds, that JDA will wait between reconnect attempts.
     * <br>Can be set using {@link net.dv8tion.jda.api.JDABuilder#setMaxReconnectDelay(int) JDABuilder.setMaxReconnectDelay(int)}.
     *
     * @return The maximum amount of time JDA will wait between reconnect attempts in seconds.
     */
    int getMaxReconnectDelay();

    /**
     * Sets whether or not JDA should try to automatically reconnect if a connection-error is encountered.
     * <br>This will use an incremental reconnect (timeouts are increased each time an attempt fails).
     *
     * <p>Default is <b>true</b>.
     *
     * @param  reconnect If true - enables autoReconnect
     */
    void setAutoReconnect(boolean reconnect);

    /**
     * Whether the Requester should retry when
     * a {@link java.net.SocketTimeoutException SocketTimeoutException} occurs.
     *
     * @param  retryOnTimeout
     *         True, if the Request should retry once on a socket timeout
     */
    void setRequestTimeoutRetry(boolean retryOnTimeout);

    /**
     * USed to determine whether or not autoReconnect is enabled for JDA.
     *
     * @return True if JDA will attempt to automatically reconnect when a connection-error is encountered.
     */
    boolean isAutoReconnect();

    /**
     * Used to determine whether the instance of JDA supports audio and has it enabled.
     *
     * @return True if JDA can currently utilize the audio system.
     */
    boolean isAudioEnabled();

    /**
     * Used to determine if JDA will process MESSAGE_DELETE_BULK messages received from Discord as a single
     * {@link net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent MessageBulkDeleteEvent} or split
     * the deleted messages up and fire multiple {@link net.dv8tion.jda.api.events.message.MessageDeleteEvent MessageDeleteEvents},
     * one for each deleted message.
     *
     * <p>By default, JDA will separate the bulk delete event into individual delete events, but this isn't as efficient as
     * handling a single event would be. It is recommended that BulkDelete Splitting be disabled and that the developer
     * should instead handle the {@link net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent MessageBulkDeleteEvent}
     *
     * @return Whether or not JDA currently handles the BULK_MESSAGE_DELETE event by splitting it into individual MessageDeleteEvents or not.
     */
    boolean isBulkDeleteSplittingEnabled();

    /**
     * Shuts down this JDA instance, closing all its connections.
     * After this command is issued the JDA Instance can not be used anymore.
     * Already enqueued {@link net.dv8tion.jda.api.requests.RestAction RestActions} are still going to be executed.
     *
     * <p>If you want this instance to shutdown without executing, use {@link #shutdownNow() shutdownNow()}
     *
     * @see #shutdownNow()
     */
    void shutdown();

    /**
     * Shuts down this JDA instance instantly, closing all its connections.
     * After this command is issued the JDA Instance can not be used anymore.
     * This will also cancel all queued {@link net.dv8tion.jda.api.requests.RestAction RestActions}.
     *
     * <p>If you want this instance to shutdown without cancelling enqueued RestActions use {@link #shutdown() shutdown()}
     *
     * @see #shutdown()
     */
    void shutdownNow();

    ///**
    // * Installs an auxiliary cable into the given port of your system.
    // *
    // * @param  port
    // *         The port in which the cable should be installed.
    // *
    // * @return {@link net.dv8tion.jda.api.requests.restaction.AuditableRestAction AuditableRestAction}{@literal <}{@link Void}{@literal >}
    // */
    //AuditableRestAction<Void> installAuxiliaryCable(int port);

    /**
     * The {@link net.dv8tion.jda.api.AccountType} of the currently logged in account.
     * <br>Used when determining functions that are restricted based on the type of account.
     *
     * @return The current AccountType.
     */
    AccountType getAccountType();

    /**
     * Retrieves the {@link ApplicationInfo ApplicationInfo} for
     * the application that owns the logged in Bot-Account.
     * <br>This contains information about the owner of the currently logged in bot account!
     *
     * @throws net.dv8tion.jda.api.exceptions.AccountTypeException
     *         If the currently logged in account is not from {@link net.dv8tion.jda.api.AccountType#BOT AccountType.BOT}
     *
     * @return {@link net.dv8tion.jda.api.requests.RestAction RestAction} - Type: {@link ApplicationInfo ApplicationInfo}
     *         <br>The {@link ApplicationInfo ApplicationInfo} of the bot's application.
     */
    @CheckReturnValue
    RestAction<ApplicationInfo> retrieveApplicationInfo();

    /**
     * Creates an authorization invite url for the currently logged in Bot-Account.
     * <br>Example Format:
     * {@code https://discordapp.com/oauth2/authorize?scope=bot&client_id=288202953599221761&permissions=8}
     *
     * <p><b>Hint:</b> To enable a pre-selected Guild of choice append the parameter {@code &guild_id=YOUR_GUILD_ID}
     *
     * @param  permissions
     *         The permissions to use in your invite, these can be changed by the link user.
     *         <br>If no permissions are provided the {@code permissions} parameter is omitted
     *
     * @throws net.dv8tion.jda.api.exceptions.AccountTypeException
     *         If the currently logged in account is not from {@link net.dv8tion.jda.api.AccountType#BOT AccountType.BOT}
     *
     * @return A valid OAuth2 invite url for the currently logged in Bot-Account
     */
    String getInviteUrl(Permission... permissions);

    /**
     * Creates an authorization invite url for the currently logged in Bot-Account.
     * <br>Example Format:
     * {@code https://discordapp.com/oauth2/authorize?scope=bot&client_id=288202953599221761&permissions=8}
     *
     * <p><b>Hint:</b> To enable a pre-selected Guild of choice append the parameter {@code &guild_id=YOUR_GUILD_ID}
     *
     * @param  permissions
     *         The permissions to use in your invite, these can be changed by the link user.
     *         <br>If no permissions are provided the {@code permissions} parameter is omitted
     *
     * @throws net.dv8tion.jda.api.exceptions.AccountTypeException
     *         If the currently logged in account is not from {@link net.dv8tion.jda.api.AccountType#BOT AccountType.BOT}
     *
     * @return A valid OAuth2 invite url for the currently logged in Bot-Account
     */
    String getInviteUrl(Collection<Permission> permissions);

    /**
     * Returns the {@link net.dv8tion.jda.api.sharding.ShardManager ShardManager} that manages this JDA instances or null if this instance is not managed
     * by any {@link net.dv8tion.jda.api.sharding.ShardManager ShardManager}.
     *
     * @return The corresponding ShardManager or {@code null} if there is no such manager
     */
    ShardManager getShardManager();

    /**
     * Retrieves a {@link net.dv8tion.jda.api.entities.Webhook Webhook} by its id.
     *
     * <p>Possible {@link net.dv8tion.jda.api.requests.ErrorResponse ErrorResponses} caused by
     * the returned {@link net.dv8tion.jda.api.requests.RestAction RestAction} include the following:
     * <ul>
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#MISSING_PERMISSIONS MISSING_PERMISSIONS}
     *     <br>We do not have the required permissions</li>
     *
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#UNKNOWN_WEBHOOK UNKNOWN_WEBHOOK}
     *     <br>A webhook with this id does not exist</li>
     * </ul>
     *
     * @param  webhookId
     *         The webhook id
     *
     * @throws IllegalArgumentException
     *         If the {@code webhookId} is null or empty
     *
     * @return {@link net.dv8tion.jda.api.requests.RestAction RestAction} - Type: {@link net.dv8tion.jda.api.entities.Webhook Webhook}
     *          <br>The webhook object.
     *
     * @see    Guild#retrieveWebhooks()
     * @see    TextChannel#retrieveWebhooks()
     */
    RestAction<Webhook> retrieveWebhookById(String webhookId);

    /**
     * Retrieves a {@link net.dv8tion.jda.api.entities.Webhook Webhook} by its id.
     *
     * <p>Possible {@link net.dv8tion.jda.api.requests.ErrorResponse ErrorResponses} caused by
     * the returned {@link net.dv8tion.jda.api.requests.RestAction RestAction} include the following:
     * <ul>
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#MISSING_PERMISSIONS MISSING_PERMISSIONS}
     *     <br>We do not have the required permissions</li>
     *
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#UNKNOWN_WEBHOOK UNKNOWN_WEBHOOK}
     *     <br>A webhook with this id does not exist</li>
     * </ul>
     *
     * @param  webhookId
     *         The webhook id
     *
     * @return {@link net.dv8tion.jda.api.requests.RestAction RestAction} - Type: {@link net.dv8tion.jda.api.entities.Webhook Webhook}
     *          <br>The webhook object.
     *
     * @see    Guild#retrieveWebhooks()
     * @see    TextChannel#retrieveWebhooks()
     */
    default RestAction<Webhook> retrieveWebhookById(long webhookId)
    {
        return retrieveWebhookById(Long.toUnsignedString(webhookId));
    }
}