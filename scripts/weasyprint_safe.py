#!/usr/bin/env python3
"""
Safe WeasyPrint wrapper for OctaPDF (SEC-01).
Enforces strict url_fetcher to prevent SSRF and arbitrary local file reads.
"""
import os
import sys
from urllib.parse import urlparse

try:
    import weasyprint
    from weasyprint import HTML, default_url_fetcher
except ImportError:
    pass

def create_safe_url_fetcher(allowed_dir):
    real_allowed = os.path.realpath(allowed_dir)
    def safe_url_fetcher(url):
        parsed = urlparse(url)
        scheme = parsed.scheme.lower()
        if scheme in ('http', 'https', 'ftp', 'ftps'):
            # Block all external network requests (SSRF defense)
            raise ValueError(f"External network fetch blocked for security: {url}")
        if scheme == 'data':
            return default_url_fetcher(url)
        
        target_path = None
        if scheme == 'file':
            path = parsed.path
            if os.name == 'nt' and path.startswith('/') and len(path) > 2 and path[2] == ':':
                path = path[1:]
            target_path = path
        elif not scheme:
            # Relative path within allowed_dir
            target_path = os.path.join(real_allowed, url)
        
        if target_path:
            real_target = os.path.realpath(target_path)
            try:
                # Disallow directory traversal outside allowed_dir using commonpath
                if os.path.commonpath([real_allowed, real_target]) == real_allowed:
                    return default_url_fetcher(real_target if not scheme else f"file://{real_target}")
            except (ValueError, Exception):
                pass
            raise ValueError(f"Local file access outside allowed directory blocked: {url}")

        raise ValueError(f"Blocked URL scheme: {scheme}")
    return safe_url_fetcher

def main():
    args = sys.argv[1:]
    positional = [arg for arg in args if not arg.startswith('-')]
    if len(positional) < 2:
        from weasyprint.__main__ import main as weasy_main
        sys.exit(weasy_main())
    
    input_file = positional[-2]
    output_file = positional[-1]
    allowed_dir = os.path.dirname(os.path.realpath(input_file))
    
    html = HTML(filename=input_file, url_fetcher=create_safe_url_fetcher(allowed_dir))
    html.write_pdf(target=output_file)

if __name__ == '__main__':
    main()
