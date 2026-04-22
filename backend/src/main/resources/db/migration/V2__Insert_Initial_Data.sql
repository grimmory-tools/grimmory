INSERT INTO app_settings (category, name, val)
VALUES
    (
        'quick_book_match',
        'all_books',
        '{
            "allP1": "Amazon",
            "allP2": "GoodReads",
            "allP3": "Google",
            "refreshCovers": true,
            "mergeCategories": true,
            "fieldOptions": {
                "title": {
                    "p1": "Amazon",
                    "p2": "GoodReads",
                    "p3": "Google"
                },
                "description": {
                    "p1": "Amazon",
                    "p2": "GoodReads",
                    "p3": "Google"
                },
                "authors": {
                    "p1": "Amazon",
                    "p2": "GoodReads",
                    "p3": "Google"
                },
                "categories": {
                    "p1": "GoodReads",
                    "p2": "Amazon",
                    "p3": "Google"
                },
                "cover": {
                    "p1": "Amazon",
                    "p2": "GoodReads",
                    "p3": "Google"
                }
            }
        }'
    );