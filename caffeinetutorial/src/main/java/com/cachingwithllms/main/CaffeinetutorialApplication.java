package com.cachingwithllms.main;

import com.cachingwithllms.main.CaffeinetutorialApplication;
import com.github.benmanes.caffeine.cache.*;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A Spring Boot application demonstrating a single Manual Caffeine cache with:
 * - short maximumSize=3
 * - short expireAfterWrite=15s
 * for quick, visible evictions in a short class presentation.
 */
@SpringBootApplication
public class CaffeinetutorialApplication {

    public static void main(String[] args) {
        SpringApplication.run(CaffeinetutorialApplication.class, args);
    }

    // -----------------------------------------------------------------------
    // TOY LLM ENTITIES
    // -----------------------------------------------------------------------

    /**
     * A simple record representing a user prompt to an LLM.
     */
    public record Prompt(String text) {}

    /**
     * A simple record representing a response from an LLM (with a timestamp).
     */
    public record LLMResponse(String answer, long createdAtMillis) {
        @Override
        public String toString() {
            return "LLMResponse(answer=\"" + answer + "\", createdAt=" + createdAtMillis + ")";
        }
    }

    // -----------------------------------------------------------------------
    // SERVICE: Manages only a MANUAL Caffeine cache
    // -----------------------------------------------------------------------
    @Repository
    class LLMCacheService {

        // The single Manual cache
        private final Cache<Prompt, LLMResponse> manualCache;

        LLMCacheService() {
            // Manual cache with a short size limit and short expiration
            manualCache = Caffeine.newBuilder()
                .maximumSize(3)
                .expireAfterWrite(Duration.ofSeconds(15))
                .recordStats()
                .removalListener((Prompt key, LLMResponse value, RemovalCause cause) ->
                    System.out.printf("[MANUAL] Removed %s -> %s (cause: %s)%n", key, value, cause))
                .build();
        }

        /**
         * Retrieve from the MANUAL cache. If absent, compute the response.
         */
        public LLMResponse getManual(Prompt prompt) {
            return manualCache.get(prompt, p -> createExpensiveResponse(p.text()));
        }

        /**
         * Invalidate a single key from the cache.
         */
        public void invalidatePrompt(Prompt prompt) {
            manualCache.invalidate(prompt);
        }

        /**
         * Invalidate all entries in the cache.
         */
        public void invalidateAll() {
            manualCache.invalidateAll();
        }

        /**
         * Return cache stats in a user-friendly map format.
         */
        public Map<String, Object> getStats() {
            CacheStats stats = manualCache.stats();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("Hit Count", stats.hitCount());
            map.put("Miss Count", stats.missCount());
            map.put("Load Success", stats.loadSuccessCount());
            map.put("Load Failure", stats.loadFailureCount());
            map.put("Total Load Time", stats.totalLoadTime());
            map.put("Eviction Count", stats.evictionCount());
            map.put("Eviction Weight", stats.evictionWeight());
            map.put("Hit Rate", stats.hitRate());
            return map;
        }

        /**
         * Returns the current contents of the manual cache.
         */
        public Map<String, String> getAllData() {
            Map<String, String> manualData = new LinkedHashMap<>();
            manualCache.asMap().forEach((prompt, response) ->
                manualData.put(prompt.text(), response.toString())
            );
            return manualData;
        }

        /**
         * Simulate an expensive synchronous LLM computation.
         */
        private LLMResponse createExpensiveResponse(String promptText) {
            try {
                // Sleep 300-500ms to emulate real cost
                Thread.sleep(300 + ThreadLocalRandom.current().nextInt(200));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            long now = System.currentTimeMillis();
            return new LLMResponse("Answer to: " + promptText, now);
        }
    }

    // -----------------------------------------------------------------------
    // CONTROLLER: REST endpoints (only manual cache routes)
    // -----------------------------------------------------------------------
    @RestController
    @RequestMapping("/api")
    class LLMCacheController {

        @Autowired
        LLMCacheService cacheService;

        // Single route to get from manual cache
        @GetMapping("/manual/{text}")
        public LLMResponse getManual(@PathVariable("text") String text) {
            return cacheService.getManual(new Prompt(text));
        }

        @GetMapping("/invalidate/{text}")
        public String invalidatePrompt(@PathVariable("text") String text) {
            cacheService.invalidatePrompt(new Prompt(text));
            return "Invalidated prompt: " + text;
        }

        @GetMapping("/invalidateAll")
        public String invalidateAll() {
            cacheService.invalidateAll();
            return "All prompts invalidated!";
        }

        @GetMapping("/stats")
        public Map<String, Object> stats() {
            return cacheService.getStats();
        }

        @GetMapping("/data")
        public Map<String, String> data() {
            return cacheService.getAllData();
        }
    }

    // -----------------------------------------------------------------------
    // CONTROLLER: Minimal "ChatGPT-style" UI (HTML + JS + CSS) at root "/"
    // -----------------------------------------------------------------------
    @RestController
    class UIController {

        @GetMapping("/")
        public String index() {
            // Only one cache type: "Manual" -> We'll remove the dropdown.
            return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <title>LLM Cache</title>
                  <style>
                    body {
                      margin: 0; 
                      padding: 0;
                      background-color: #343541; /* ChatGPT-like dark background */
                      color: #ececf1;
                      font-family: "Segoe UI", Tahoma, Geneva, Verdana, sans-serif;
                    }
                    .container {
                      display: flex;
                      flex-direction: column;
                      max-width: 900px;
                      margin: 0 auto;
                      padding: 20px;
                    }
                    h1 {
                      text-align: center;
                      margin-bottom: 20px;
                    }
                    .chat-box {
                      background-color: #444654;
                      border-radius: 8px;
                      padding: 15px;
                      margin-bottom: 15px;
                      height: 350px;
                      display: flex;
                      flex-direction: column;
                      gap: 5px;
                      overflow-y: auto;
                    }
                    .message {
                      background-color: #40414f;
                      border-radius: 5px;
                      padding: 10px;
                      margin-bottom: 5px;
                      white-space: pre-wrap;
                    }
                    .user {
                      align-self: flex-end;
                      background-color: #525f7f;
                    }
                    .system {
                      align-self: flex-start;
                    }
                    .controls {
                      display: flex;
                      gap: 10px;
                      margin-bottom: 15px;
                    }
                    .controls input[type=text] {
                      flex: 1;
                      padding: 8px;
                      border-radius: 4px;
                      border: none;
                    }
                    .controls button {
                      background-color: #00a57b;
                      color: white;
                      border: none;
                      border-radius: 4px;
                      padding: 10px;
                      cursor: pointer;
                    }
                    .controls button:hover {
                      background-color: #00c08b;
                    }
                    .styled-buttons {
                      display: flex; 
                      gap: 10px; 
                      margin-bottom: 10px;
                    }
                    .stats-button {
                      background-color: #9b59b6; 
                      color: #fff;
                      border: none;
                      border-radius: 4px;
                      padding: 10px;
                      cursor: pointer;
                    }
                    .stats-button:hover {
                      background-color: #b370cf;
                    }
                    .data-button {
                      background-color: #d35400; 
                      color: #fff;
                      border: none;
                      border-radius: 4px;
                      padding: 10px;
                      cursor: pointer;
                    }
                    .data-button:hover {
                      background-color: #e98b39;
                    }
                    .invalidate-controls {
                      display: flex;
                      gap: 10px;
                      margin-top: 10px;
                    }
                    .info-panel {
                      background-color: #202123;
                      padding: 10px;
                      border-radius: 5px;
                      margin-top: 10px;
                    }
                    #statsArea, #dataArea {
                      margin-bottom: 10px;
                    }
                    .sectionTitle {
                      color: #00c08b;
                      margin-bottom: 5px;
                      font-weight: bold;
                      font-size: 1.1em;
                    }
                    table {
                      border-collapse: collapse;
                      margin-left: 10px;
                    }
                    td {
                      padding: 4px 10px;
                    }
                    td.key {
                      color: #ccc;
                    }
                  </style>
                </head>
                <body>
                  <div class="container">
                    <h1>LLM Cache</h1>
                    
                    <div class="chat-box" id="chat"></div>
                    
                    <div class="controls">
                      <input type="text" id="promptInput" placeholder="Enter your prompt..." />
                      <button onclick="ask()">Ask</button>
                    </div>
                    
                    <div class="styled-buttons">
                      <button class="stats-button" onclick="refreshStats()">Refresh Stats</button>
                      <button class="data-button" onclick="refreshData()">Refresh Data</button>
                    </div>
                    
                    <div class="invalidate-controls">
                      <input type="text" id="invalidateKey" placeholder="Key to invalidate" />
                      <button onclick="invalidateOne()">Invalidate Key</button>
                      <button onclick="invalidateAll()">Invalidate All</button>
                    </div>
                    
                    <div class="info-panel" id="statsArea"></div>
                    <div class="info-panel" id="dataArea"></div>
                  </div>
                  
                  <script>
                    // On page load, show initial stats/data
                    window.onload = () => {
                      refreshStats();
                      refreshData();
                    };
                    
                    // Append messages to the chat box
                    function appendMessage(msg, isUser = false) {
                      const chat = document.getElementById('chat');
                      const div = document.createElement('div');
                      div.className = 'message ' + (isUser ? 'user' : 'system');
                      div.textContent = msg;
                      chat.appendChild(div);
                      chat.scrollTop = chat.scrollHeight; // auto-scroll
                    }
                    
                    // "Ask" the single Manual cache
                    async function ask() {
                      const promptInput = document.getElementById('promptInput');
                      const promptText = promptInput.value.trim();
                      if (!promptText) return;
                      
                      appendMessage(promptText, true);
                      promptInput.value = '';
                      
                      try {
                        const url = '/api/manual/' + encodeURIComponent(promptText);
                        const response = await fetch(url);
                        if (!response.ok) {
                          appendMessage('[Error ' + response.status + ']', false);
                          return;
                        }
                        const data = await response.json();
                        appendMessage(data.answer + " (cached @ " + data.createdAtMillis + ")", false);
                        
                        // Update stats/data after each request
                        refreshStats();
                        refreshData();
                      } catch (err) {
                        console.error(err);
                        appendMessage('[Error: ' + err.message + ']', false);
                      }
                    }
                    
                    // Refresh stats from /api/stats
                    async function refreshStats() {
                      const statsEl = document.getElementById('statsArea');
                      try {
                        const resp = await fetch('/api/stats');
                        const stats = await resp.json();
                        statsEl.innerHTML = formatStats(stats);
                      } catch (err) {
                        statsEl.textContent = 'Stats Error: ' + err.message;
                      }
                    }
                    
                    // Refresh data from /api/data
                    async function refreshData() {
                      const dataEl = document.getElementById('dataArea');
                      try {
                        const resp = await fetch('/api/data');
                        const cacheData = await resp.json();
                        dataEl.innerHTML = formatData(cacheData);
                      } catch (err) {
                        dataEl.textContent = 'Data Error: ' + err.message;
                      }
                    }
                    
                    // Invalidate a single key
                    async function invalidateOne() {
                      const keyInput = document.getElementById('invalidateKey');
                      const text = keyInput.value.trim();
                      if (!text) return;
                      keyInput.value = '';
                      try {
                        const response = await fetch('/api/invalidate/' + encodeURIComponent(text));
                        const msg = await response.text();
                        appendMessage(msg, false);
                        refreshStats();
                        refreshData();
                      } catch (err) {
                        appendMessage('Invalidate Error: ' + err.message, false);
                      }
                    }
                    
                    // Invalidate all
                    async function invalidateAll() {
                      try {
                        const response = await fetch('/api/invalidateAll');
                        const msg = await response.text();
                        appendMessage(msg, false);
                        refreshStats();
                        refreshData();
                      } catch (err) {
                        appendMessage('Invalidate All Error: ' + err.message, false);
                      }
                    }
                    
                    // Format stats (only one cache now)
                    function formatStats(stats) {
                      // Example: { "Hit Count": 0, "Miss Count": 2, ... }
                      let html = '<div class="sectionTitle">Cache Statistics</div>';
                      html += '<table>';
                      for (const key in stats) {
                        html += '<tr>';
                        html += `<td class="key">${key}</td>`;
                        html += `<td>${stats[key]}</td>`;
                        html += '</tr>';
                      }
                      html += '</table>';
                      return html;
                    }
                    
                    // Format data (cache contents)
                    function formatData(data) {
                      // Example: { "Hello": "LLMResponse(...)","Test":"..." }
                      let html = '<div class="sectionTitle">Cache Contents</div>';
                      const keys = Object.keys(data);
                      if (keys.length === 0) {
                        html += '<div style="margin-left:10px;">(No entries)</div>';
                      } else {
                        keys.forEach(k => {
                          html += `<div style="margin-left:10px;"><span style="color:#ccc;">${k}:</span> ${data[k]}</div>`;
                        });
                      }
                      return html;
                    }
                  </script>
                </body>
                </html>
                """;
        }
    }

}
