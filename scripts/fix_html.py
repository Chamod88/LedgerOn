import os
import re

STEP_10_PATH = r"C:\Users\ASUS\.gemini\antigravity\brain\46179506-acb9-4607-a5f7-52e5748e02fa\.system_generated\steps\10\content.md"
OUTPUT_PATH = r"c:\Users\ASUS\Documents\Chamod Music\Programming\Highthoughtput Ledger system\Documentations\design-fraud-detection.html"

def make_html_offline_friendly(html_content, base_url):
    # This function rewrites relative URLs (src="/...", href="/...") to absolute URLs
    def replace_url(match):
        attr = match.group(1)
        url = match.group(2)
        
        # Skip absolute urls, anchors, javascript, mailto
        if (url.startswith('http://') or 
            url.startswith('https://') or 
            url.startswith('#') or 
            url.startswith('javascript:') or 
            url.startswith('mailto:') or
            url.startswith('data:')):
            return f'{attr}="{url}"'
        
        if url.startswith('//'):
            return f'{attr}="https:{url}"'
        
        if url.startswith('/'):
            return f'{attr}="{base_url}{url}"'
        
        return f'{attr}="{base_url}/{url}"'

    pattern = re.compile(r'(href|src)=["\']([^"\']*)["\']', re.IGNORECASE)
    modified_html = pattern.sub(replace_url, html_content)
    return modified_html

def clean_algoroq_html():
    with open(STEP_10_PATH, 'r', encoding='utf-8') as f:
        content = f.read()
    
    parts = content.split('---', 1)
    if len(parts) > 1:
        html_raw = parts[1].strip()
    else:
        html_raw = content.strip()
    
    # 1. Convert <astro-island> to <div> to bypass Astro's custom hydration element
    html_raw = html_raw.replace('<astro-island', '<div')
    html_raw = html_raw.replace('</astro-island>', '</div>')
    
    # 2. Strip out all <script>...</script> tags to prevent CORS errors on file:// protocol
    # and prevent any script from emptying or rewriting the server-side rendered DOM.
    html_clean = re.sub(r'<script\b[^>]*>([\s\S]*?)</script>', '', html_raw, flags=re.IGNORECASE)
    
    # 3. Rewrite relative URLs to absolute
    base_url = "https://algoroq.com"
    final_html = make_html_offline_friendly(html_clean, base_url)
    
    # Write the output file
    with open(OUTPUT_PATH, 'w', encoding='utf-8') as f:
        f.write(final_html)
    
    print("Successfully processed and saved design-fraud-detection.html!")

if __name__ == "__main__":
    clean_algoroq_html()
