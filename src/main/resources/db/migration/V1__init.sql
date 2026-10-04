CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE users (
    id UUID PRIMARY KEY,
    keycloak_id UUID NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(19,2) NOT NULL,
    stock INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_products_category
      FOREIGN KEY (category_id)
          REFERENCES categories(id),

    CONSTRAINT chk_products_price
      CHECK (price >= 0),

    CONSTRAINT chk_products_stock
      CHECK (stock >= 0)
);

CREATE TABLE carts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_carts_user
    FOREIGN KEY (user_id)
       REFERENCES users(id)
);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_cart_items_cart
        FOREIGN KEY (cart_id)
            REFERENCES carts(id)
            ON DELETE CASCADE,

    CONSTRAINT fk_cart_items_product
        FOREIGN KEY (product_id)
            REFERENCES products(id),

    CONSTRAINT uq_cart_items_cart_product
        UNIQUE (cart_id, product_id),

    CONSTRAINT chk_cart_items_quantity
        CHECK (quantity > 0)
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    total DECIMAL(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id)
            REFERENCES users(id),

    CONSTRAINT chk_orders_total
        CHECK (total >= 0),

    CONSTRAINT chk_orders_status
        CHECK (
            status IN (
                       'CREATED',
                       'PAYMENT_PENDING',
                       'PAYMENT_DECLINED',
                       'PAID',
                       'CANCELLED'
                )
            )
);


CREATE TABLE order_items (
     id UUID PRIMARY KEY,
     order_id UUID NOT NULL,
     product_id UUID NOT NULL,
     name VARCHAR(255) NOT NULL,
     unit_price DECIMAL(19,2) NOT NULL,
     quantity INTEGER NOT NULL,
     subtotal DECIMAL(19,2) NOT NULL,

     CONSTRAINT fk_order_items_order
         FOREIGN KEY (order_id)
             REFERENCES orders(id)
             ON DELETE CASCADE,

     CONSTRAINT fk_order_items_product
         FOREIGN KEY (product_id)
             REFERENCES products(id),

     CONSTRAINT chk_order_items_quantity
         CHECK (quantity > 0),

     CONSTRAINT chk_order_items_unit_price
         CHECK (unit_price >= 0),

     CONSTRAINT chk_order_items_subtotal
         CHECK (subtotal >= 0)
);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(255),
    amount DECIMAL(19,2) NOT NULL,
    transaction_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_payments_order
      FOREIGN KEY (order_id)
          REFERENCES orders(id),

    CONSTRAINT chk_payments_amount
      CHECK (amount >= 0),

    CONSTRAINT chk_payments_status
      CHECK (
          status IN (
                     'PENDING',
                     'APPROVED',
                     'DECLINED',
                     'REFUNDED'
              )
          )
);

CREATE INDEX idx_products_name_trgm
    ON products USING gin (name gin_trgm_ops);

CREATE INDEX idx_products_category_active
    ON products (category_id)
    WHERE is_active = TRUE;

CREATE INDEX idx_orders_user_created
    ON orders (user_id, created_at DESC);

CREATE INDEX idx_order_items_order_id
    ON order_items (order_id);