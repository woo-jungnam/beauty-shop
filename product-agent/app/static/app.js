document.addEventListener("DOMContentLoaded", () => {
    const chatContainer = document.getElementById("chat-container");
    const chatForm = document.getElementById("chat-form");
    const userInput = document.getElementById("user-input");
    const btnSend = document.getElementById("btn-send");
    const typingIndicator = document.getElementById("typing-indicator");
    const btnNewChat = document.getElementById("btn-new-chat");
    const sessionDisplay = document.getElementById("session-id-display");
    const promptPills = document.querySelectorAll(".prompt-pill");

    let sessionId = getOrCreateSessionId();
    updateSessionUI();

    function stripEmojis(text) {
        if (!text) return "";
        return String(text)
            .replace(/[\p{Extended_Pictographic}\u2600-\u27BF\u2300-\u23FF\u2B50-\u2B55\uFE0E\uFE0F\u200D]/gu, "")
            .replace(/\s{2,}/g, " ")
            .trim();
    }

    function getOrCreateSessionId() {
        let stored = localStorage.getItem("cosmetics_session_id");
        if (!stored) {
            stored = "sess_" + Math.random().toString(36).substring(2, 10);
            localStorage.setItem("cosmetics_session_id", stored);
        }
        return stored;
    }

    function createNewSession() {
        sessionId = "sess_" + Math.random().toString(36).substring(2, 10);
        localStorage.setItem("cosmetics_session_id", sessionId);
        updateSessionUI();

        const welcome = document.getElementById("welcome-message");
        chatContainer.innerHTML = "";
        if (welcome) {
            chatContainer.appendChild(welcome);
        }
    }

    function updateSessionUI() {
        if (sessionDisplay) {
            sessionDisplay.textContent = sessionId;
        }
    }

    userInput.addEventListener("input", () => {
        userInput.style.height = "auto";
        userInput.style.height = Math.min(userInput.scrollHeight, 120) + "px";
    });

    userInput.addEventListener("keydown", (e) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            chatForm.dispatchEvent(new Event("submit"));
        }
    });

    promptPills.forEach((pill) => {
        pill.addEventListener("click", () => {
            const promptText = pill.getAttribute("data-prompt");
            if (promptText) {
                userInput.value = promptText;
                chatForm.dispatchEvent(new Event("submit"));
            }
        });
    });

    btnNewChat.addEventListener("click", () => {
        if (confirm("Bắt đầu cuộc trò chuyện mới? (Lịch sử tư vấn cũ sẽ được làm mới)")) {
            createNewSession();
        }
    });

    chatForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const text = userInput.value.trim();
        if (!text) return;

        appendUserMessage(text);
        userInput.value = "";
        userInput.style.height = "auto";
        btnSend.disabled = true;

        showTyping(true);
        scrollToBottom();

        try {
            const response = await fetch("/chat/stream", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    session_id: sessionId,
                    message: text,
                }),
            });

            if (!response.ok) {
                throw new Error(`Lỗi kết nối máy chủ (${response.status})`);
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder("utf-8");
            let buffer = "";
            let currentText = "";
            let products = [];
            let botRow = null;
            let messageContentEl = null;

            function ensureBotRow() {
                if (!botRow) {
                    showTyping(false);
                    botRow = document.createElement("div");
                    botRow.className = "message-row bot-row";
                    botRow.innerHTML = `
                        <div class="message-avatar">AI</div>
                        <div class="message-bubble bot-bubble">
                            <div class="message-content"></div>
                        </div>
                    `;
                    chatContainer.appendChild(botRow);
                    messageContentEl = botRow.querySelector(".message-content");
                }
            }

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split("\n");
                buffer = lines.pop() || "";

                for (const line of lines) {
                    const trimmed = line.trim();
                    if (!trimmed.startsWith("data: ")) continue;

                    const jsonStr = trimmed.substring(6).trim();
                    if (!jsonStr) continue;

                    try {
                        const eventData = JSON.parse(jsonStr);

                        if (eventData.type === "metadata") {
                            products = eventData.products || [];
                        } else if (eventData.type === "token") {
                            ensureBotRow();
                            currentText += eventData.content || "";
                            messageContentEl.innerHTML = parseMarkdown(currentText);
                            scrollToBottom();
                        } else if (eventData.type === "error") {
                            throw new Error(eventData.detail || "Lỗi xử lý luồng AI");
                        } else if (eventData.type === "done") {
                        }
                    } catch (parseErr) {
                        console.warn("SSE Parse notice:", parseErr, jsonStr);
                    }
                }
            }

            ensureBotRow();
            showTyping(false);

            let finalHtml = parseMarkdown(stripEmojis(currentText || "Đã nhận yêu cầu của bạn."));
            if (products && products.length > 0) {
                finalHtml += `<div class="products-showcase">` + 
                    products.map((p) => renderProductCard(p)).join("") + 
                    `</div>`;
            }

            const clarifyChips = getClarificationChips(currentText);
            if (clarifyChips) {
                finalHtml += clarifyChips;
            }

            messageContentEl.innerHTML = finalHtml;
            bindCardButtons(botRow);

        } catch (error) {
            showTyping(false);
            appendBotMessage(
                `Đã xảy ra lỗi khi kết nối tới máy chủ: ${error.message}. Vui lòng thử lại sau giây lát!`,
                []
            );
        } finally {
            btnSend.disabled = false;
            userInput.focus();
            scrollToBottom();
        }
    });

    function appendUserMessage(text) {
        const row = document.createElement("div");
        row.className = "message-row user-row";
        row.innerHTML = `
            <div class="message-avatar">Bạn</div>
            <div class="message-bubble user-bubble">
                <div class="message-content">
                    <p>${escapeHtml(stripEmojis(text))}</p>
                </div>
            </div>
        `;
        chatContainer.appendChild(row);
    }

    function appendBotMessage(markdownText, products) {
        const row = document.createElement("div");
        row.className = "message-row bot-row";

        const cleanText = stripEmojis(markdownText);
        let contentHtml = parseMarkdown(cleanText);
        if (products && products.length > 0) {
            contentHtml += `<div class="products-showcase">` + 
                products.map((p) => renderProductCard(p)).join("") + 
                `</div>`;
        }

        const clarifyChips = getClarificationChips(cleanText);
        if (clarifyChips) {
            contentHtml += clarifyChips;
        }

        row.innerHTML = `
            <div class="message-avatar">AI</div>
            <div class="message-bubble bot-bubble">
                <div class="message-content">
                    ${contentHtml}
                </div>
            </div>
        `;
        chatContainer.appendChild(row);
        bindCardButtons(row);
    }

    let cart = [];
    const cartCountEl = document.getElementById("cart-count");
    const headerCartEl = document.getElementById("header-cart");

    if (headerCartEl) {
        headerCartEl.addEventListener("click", () => {
            if (cart.length === 0) {
                showToast("Giỏ hàng của bạn đang trống. Hãy chọn sản phẩm tư vấn!", "info");
                return;
            }
            const total = cart.reduce((sum, item) => sum + (item.price || 0), 0);
            const totalFormatted = total.toLocaleString("vi-VN") + " đ";
            showToast(`Giỏ hàng có ${cart.length} sản phẩm. Tổng cộng: ${totalFormatted}`, "success");
        });
    }

    function addToCart(product) {
        cart.push(product);
        if (cartCountEl) {
            cartCountEl.textContent = cart.length;
            cartCountEl.classList.add("cart-bump");
            setTimeout(() => cartCountEl.classList.remove("cart-bump"), 300);
        }
        showToast(`Đã thêm "${product.name}" vào giỏ hàng!`, "success");
    }

    function showToast(message, type = "success") {
        const container = document.getElementById("toast-container");
        if (!container) return;

        const toast = document.createElement("div");
        toast.className = `toast-item toast-${type}`;
        const icon = type === "success" ? "OK" : "i";
        toast.innerHTML = `
            <span class="toast-icon">${icon}</span>
            <span class="toast-msg">${escapeHtml(message)}</span>
        `;
        container.appendChild(toast);

        setTimeout(() => {
            toast.classList.add("toast-fade-out");
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    }

    function renderProductCard(p) {
        const isService = (p.target_type || "").toUpperCase() === "SERVICE";
        const priceFormatted = Number(p.price || 0).toLocaleString("vi-VN") + " đ";
        const reviewsCount = Number(p.total_reviews || 0).toLocaleString("vi-VN");
        const soldCount = Number(p.total_sold || 0).toLocaleString("vi-VN");
        const ratingVal = (Number(p.rating || 5.0)).toFixed(1);

        const badges = [];
        if (isService) {
            badges.push(`<span class="match-badge badge-purple">Dịch vụ Spa</span>`);
            if (p.duration_minutes) {
                badges.push(`<span class="match-badge badge-gray">${p.duration_minutes} phút</span>`);
            }
        } else {
            badges.push(`<span class="match-badge badge-blue-light">Sản phẩm</span>`);
        }

        const reason = (p.reason_for_recommendation || "").toLowerCase();
        const pNameLower = (p.name || "").toLowerCase();
        const ingr = (p.key_ingredients || "").toLowerCase();

        if (pNameLower.includes("mẹ bầu") || pNameLower.includes("bầu") || reason.includes("mẹ bầu") || reason.includes("an toàn thai kỳ")) {
            badges.push(`<span class="match-badge badge-pink">An toàn mẹ bầu</span>`);
        }
        if (reason.includes("0% cồn") || reason.includes("không cồn") || ingr.includes("0% cồn")) {
            badges.push(`<span class="match-badge badge-green">0% Cồn khô</span>`);
        }
        if (reason.includes("không hương liệu") || ingr.includes("fragrance-free")) {
            badges.push(`<span class="match-badge badge-green">Không hương liệu</span>`);
        }

        const skins = Array.isArray(p.skin_type) ? p.skin_type.map(s => s.toLowerCase()) : [];
        if (skins.includes("oily") || skins.includes("acne_prone")) {
            badges.push(`<span class="match-badge badge-blue">Da dầu mụn</span>`);
        } else if (skins.includes("dry")) {
            badges.push(`<span class="match-badge badge-blue">Da khô</span>`);
        } else if (skins.includes("sensitive")) {
            badges.push(`<span class="match-badge badge-blue">Da nhạy cảm</span>`);
        } else if (skins.includes("all")) {
            badges.push(`<span class="match-badge badge-blue">Mọi loại da</span>`);
        }

        if (Number(p.total_sold || 0) >= 50) {
            badges.push(`<span class="match-badge badge-orange">${isService ? "Được yêu thích" : "Bán chạy"}</span>`);
        }

        const badgesHtml = badges.length > 0 ? `<div class="match-badges">${badges.join("")}</div>` : "";

        const imgHtml = p.image_url ? `
            <div class="card-img-wrap">
                <img src="${escapeHtml(p.image_url)}" alt="${escapeHtml(p.name)}" class="card-product-img" loading="lazy" onerror="this.style.display='none'">
            </div>
        ` : "";

        const actionBtn = isService ? `
            <button type="button" class="btn-card-book" data-service-id="${escapeHtml(p.id)}" data-service-name="${escapeHtml(p.name)}" title="Đặt lịch hẹn trải nghiệm dịch vụ">
                Đặt lịch ngay
            </button>
        ` : `
            <button type="button" class="btn-card-buy" data-product-id="${escapeHtml(p.id)}" data-product-name="${escapeHtml(p.name)}" data-product-price="${Number(p.price || 0)}" title="Thêm vào giỏ hàng">
                Thêm giỏ
            </button>
        `;

        return `
            <div class="product-card" id="card-${escapeHtml(p.id)}">
                <div>
                    ${imgHtml}
                    <div class="card-header-line">
                        <span class="card-brand">${escapeHtml(p.brand || (isService ? "Beauty Spa" : "Mỹ phẩm"))}</span>
                        <span class="card-price">${priceFormatted}</span>
                    </div>
                    <h3 class="card-title" title="${escapeHtml(p.name)}">${escapeHtml(p.name)}</h3>
                    ${badgesHtml}
                    <div class="card-social-proof">
                        <span class="rating-stars">${ratingVal} sao</span>
                        <span class="social-pill">${reviewsCount} đánh giá</span>
                        <span class="social-pill">${isService ? `Đã phục vụ ${soldCount}` : `Đã bán ${soldCount}`}</span>
                    </div>
                    ${p.reason_for_recommendation ? `
                        <div class="card-reason">
                            ${escapeHtml(p.reason_for_recommendation)}
                        </div>
                    ` : ""}
                </div>
                <div class="card-actions">
                    <button type="button" class="btn-card-ask" data-target-type="${isService ? 'SERVICE' : 'PRODUCT'}" data-product-name="${escapeHtml(p.name)}" title="${isService ? 'Tư vấn thêm về liệu trình này' : 'Hỏi thêm chi tiết về sản phẩm này'}">
                        ${isService ? 'Tư vấn thêm' : 'Hỏi đáp'}
                    </button>
                    ${actionBtn}
                </div>
            </div>
        `;
    }

    function getClarificationChips(text) {
        if (!text) return "";
        const lower = text.toLowerCase();
        const chips = [];

        if (lower.includes("loại da nào") || lower.includes("tình trạng da") || lower.includes("da của bạn")) {
            chips.push({ text: "Da dầu mụn", prompt: "Da mình là da dầu mụn" });
            chips.push({ text: "Da khô thiếu ẩm", prompt: "Da mình là da khô thiếu ẩm" });
            chips.push({ text: "Da nhạy cảm", prompt: "Da mình là da nhạy cảm dễ kích ứng" });
            chips.push({ text: "Da hỗn hợp", prompt: "Da mình là da hỗn hợp thiên dầu" });
        } else if (lower.includes("ngân sách") || lower.includes("mức giá") || lower.includes("tầm giá")) {
            chips.push({ text: "Dưới 200 nghìn", prompt: "Ngân sách dưới 200k" });
            chips.push({ text: "Dưới 350 nghìn", prompt: "Tầm giá dưới 350k" });
            chips.push({ text: "Dưới 600 nghìn", prompt: "Ngân sách dưới 600k" });
        }

        if (chips.length === 0) return "";

        return `
            <div class="clarification-chips">
                ${chips.map(c => `<button type="button" class="clarify-chip" data-prompt="${escapeHtml(c.prompt)}">${escapeHtml(c.text)}</button>`).join("")}
            </div>
        `;
    }

    function bindCardButtons(container) {
        if (!container) return;
        container.querySelectorAll(".btn-card-ask").forEach((btn) => {
            btn.addEventListener("click", () => {
                const pName = btn.getAttribute("data-product-name");
                const tType = btn.getAttribute("data-target-type") || "PRODUCT";
                if (tType === "SERVICE") {
                    userInput.value = `Cho mình hỏi thêm chi tiết quy trình, thời lượng và hiệu quả của dịch vụ: ${pName}`;
                } else {
                    userInput.value = `Cho mình hỏi thêm chi tiết và cách dùng của sản phẩm: ${pName}`;
                }
                chatForm.dispatchEvent(new Event("submit"));
            });
        });

        container.querySelectorAll(".btn-card-buy").forEach((btn) => {
            btn.addEventListener("click", () => {
                const id = btn.getAttribute("data-product-id");
                const name = btn.getAttribute("data-product-name");
                const price = parseFloat(btn.getAttribute("data-product-price") || "0");
                addToCart({ id, name, price });
            });
        });

        container.querySelectorAll(".btn-card-book").forEach((btn) => {
            btn.addEventListener("click", () => {
                const sName = btn.getAttribute("data-service-name");
                userInput.value = `Tôi muốn đặt lịch hẹn cho dịch vụ spa: ${sName}. Vui lòng tư vấn thời gian và chi nhánh phù hợp!`;
                chatForm.dispatchEvent(new Event("submit"));
            });
        });

        container.querySelectorAll(".clarify-chip").forEach((chip) => {
            chip.addEventListener("click", () => {
                const promptVal = chip.getAttribute("data-prompt");
                if (promptVal) {
                    userInput.value = promptVal;
                    chatForm.dispatchEvent(new Event("submit"));
                }
            });
        });
    }

    function parseMarkdown(md) {
        if (!md) return "";
        let html = escapeHtml(md);

        html = html.replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>");
        html = html.replace(/\*(.*?)\*/g, "<em>$1</em>");
        html = html.replace(/`([^`]+)`/g, "<code>$1</code>");

        const lines = html.split("\n");
        let formatted = "";
        let inList = false;
        let listType = "ul";
        let inTable = false;
        let tableHeaderDone = false;

        for (let i = 0; i < lines.length; i++) {
            let trimmed = lines[i].trim();

            if (!trimmed) {
                if (inList) {
                    formatted += `</${listType}>`;
                    inList = false;
                }
                if (inTable) {
                    formatted += `</tbody></table></div>`;
                    inTable = false;
                    tableHeaderDone = false;
                }
                continue;
            }

            // Markdown Table detection: lines containing '|'
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                if (inList) {
                    formatted += `</${listType}>`;
                    inList = false;
                }

                // Check if this line is separator like |---|---|
                if (/^\|(\s*[-:]+\s*\|)+$/.test(trimmed)) {
                    tableHeaderDone = true;
                    continue;
                }

                const cells = trimmed.slice(1, -1).split("|").map(c => c.trim());

                if (!inTable) {
                    inTable = true;
                    tableHeaderDone = false;
                    formatted += `<div class="table-responsive"><table class="chat-table"><thead><tr>`;
                    cells.forEach(c => {
                        formatted += `<th>${c}</th>`;
                    });
                    formatted += `</tr></thead><tbody>`;
                } else if (tableHeaderDone) {
                    formatted += `<tr>`;
                    cells.forEach(c => {
                        formatted += `<td>${c}</td>`;
                    });
                    formatted += `</tr>`;
                } else {
                    // Subsequent header row if any
                    formatted += `<tr>`;
                    cells.forEach(c => {
                        formatted += `<td>${c}</td>`;
                    });
                    formatted += `</tr>`;
                }
                continue;
            } else if (inTable) {
                formatted += `</tbody></table></div>`;
                inTable = false;
                tableHeaderDone = false;
            }

            if (/^(\-{3,}|\*{3,})$/.test(trimmed)) {
                if (inList) {
                    formatted += `</${listType}>`;
                    inList = false;
                }
                formatted += '<hr class="chat-divider">';
                continue;
            }

            if (trimmed.startsWith("#### ")) {
                if (inList) {
                    formatted += `</${listType}>`;
                    inList = false;
                }
                formatted += `<h4>${trimmed.substring(5)}</h4>`;
                continue;
            }
            if (trimmed.startsWith("### ")) {
                if (inList) {
                    formatted += `</${listType}>`;
                    inList = false;
                }
                formatted += `<h3>${trimmed.substring(4)}</h3>`;
                continue;
            }
            if (trimmed.startsWith("## ")) {
                if (inList) {
                    formatted += `</${listType}>`;
                    inList = false;
                }
                formatted += `<h2>${trimmed.substring(3)}</h2>`;
                continue;
            }

            if (trimmed.startsWith("&gt; ")) {
                if (inList) {
                    formatted += `</${listType}>`;
                    inList = false;
                }
                formatted += `<blockquote>${trimmed.substring(5)}</blockquote>`;
                continue;
            }

            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (!inList || listType !== "ul") {
                    if (inList) formatted += `</${listType}>`;
                    formatted += "<ul>";
                    inList = true;
                    listType = "ul";
                }
                formatted += `<li>${trimmed.substring(2)}</li>`;
                continue;
            }

            const olMatch = trimmed.match(/^(\d+)\.\s+(.*)$/);
            if (olMatch) {
                if (!inList || listType !== "ol") {
                    if (inList) formatted += `</${listType}>`;
                    formatted += "<ol>";
                    inList = true;
                    listType = "ol";
                }
                formatted += `<li>${olMatch[2]}</li>`;
                continue;
            }

            if (inList) {
                formatted += `</${listType}>`;
                inList = false;
            }
            formatted += `<p>${trimmed}</p>`;
        }

        if (inList) {
            formatted += `</${listType}>`;
        }
        if (inTable) {
            formatted += `</tbody></table></div>`;
        }

        return formatted;
    }

    function escapeHtml(text) {
        const map = {
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            '"': "&quot;",
            "'": "&#039;",
        };
        return String(text).replace(/[&<>"']/g, (m) => map[m]);
    }

    function showTyping(show) {
        if (show) {
            typingIndicator.classList.remove("hidden");
        } else {
            typingIndicator.classList.add("hidden");
        }
        scrollToBottom();
    }

    function scrollToBottom() {
        chatContainer.scrollTop = chatContainer.scrollHeight;
    }
});
